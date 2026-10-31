package com.nvan.telecomprovisioning;

import com.nvan.telecomprovisioning.provisioning.application.ServicePackageUnavailableException;
import com.nvan.telecomprovisioning.provisioning.application.SubscriptionService;
import com.nvan.telecomprovisioning.provisioning.application.SubscriptionView;
import com.nvan.telecomprovisioning.provisioning.domain.ServicePackage;
import com.nvan.telecomprovisioning.provisioning.domain.SubscriptionStatus;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.IdempotencyRecordRepository;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.ServicePackageRepository;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.SubscriptionRepository;
import com.nvan.telecomprovisioning.provisioning.messaging.EventPayloadCodec;
import com.nvan.telecomprovisioning.provisioning.messaging.OutboxEventRepository;
import com.nvan.telecomprovisioning.provisioning.messaging.ProcessedEventRepository;
import com.nvan.telecomprovisioning.provisioning.messaging.SubscriptionActivationProcessor;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class TelecomServiceProvisioningApplicationTests {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private ServicePackageRepository servicePackageRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private EventPayloadCodec eventPayloadCodec;

    @Autowired
    private SubscriptionActivationProcessor activationProcessor;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    @Transactional
    void requestSubscriptionStartsAsPendingForAnActivePackage() {
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("TEST-ACTIVE", "Active test package", true));

        var subscription = subscriptionService.requestSubscription(
                "service-test-active", "customer-1", servicePackage.getId());

        assertThat(subscription.id()).isNotNull();
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(subscription.createdAt()).isNotNull();
        assertThat(subscription.updatedAt()).isNotNull();
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    @Transactional
    void requestSubscriptionRejectsAnInactivePackage() {
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("TEST-INACTIVE", "Inactive test package", false));

        assertThatThrownBy(() -> subscriptionService.requestSubscription(
                "service-test-inactive", "customer-2", servicePackage.getId()))
                .isInstanceOf(ServicePackageUnavailableException.class);
        assertThat(subscriptionRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    @Transactional
    void listServicePackagesReturnsOnlyActivePackages() throws Exception {
        servicePackageRepository.save(new ServicePackage("API-ACTIVE", "Active package", true));
        servicePackageRepository.save(new ServicePackage("API-INACTIVE", "Inactive package", false));

        mockMvc.perform(get("/api/service-packages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("API-ACTIVE"));
    }

    @Test
    @Transactional
    void createSubscriptionReturnsCreatedLocationAndPendingStatus() throws Exception {
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("API-CREATE", "API package", true));

        mockMvc.perform(createSubscription("api-create-key", "customer-api", servicePackage.getId()))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.customerRef").value("customer-api"))
                .andExpect(jsonPath("$.packageId").value(servicePackage.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @Transactional
    void createSubscriptionReplaysTheSameResultForAnIdempotencyKey() throws Exception {
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("API-REPLAY", "Replay package", true));

        MvcResult first = mockMvc.perform(createSubscription(
                        "api-replay-key", "customer-replay", servicePackage.getId()))
                .andExpect(status().isAccepted())
                .andReturn();
        String firstLocation = first.getResponse().getHeader("Location");

        mockMvc.perform(createSubscription("api-replay-key", "customer-replay", servicePackage.getId()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", firstLocation));

        assertThat(subscriptionRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @RepeatedTest(3)
    void concurrentRequestsWithTheSameIdempotencyKeyCreateOneSubscription() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString();
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("CONCURRENT-" + uniqueSuffix, "Concurrent test package", true));
        String idempotencyKey = "concurrent-" + uniqueSuffix;
        String customerRef = "customer-concurrent";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<SubscriptionView> request = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start concurrent request");
                }
                return subscriptionService.requestSubscription(
                        idempotencyKey, customerRef, servicePackage.getId());
            };

            var firstRequest = executor.submit(request);
            var secondRequest = executor.submit(request);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            var firstResult = firstRequest.get(10, TimeUnit.SECONDS);
            var secondResult = secondRequest.get(10, TimeUnit.SECONDS);

            assertThat(firstResult.id()).isEqualTo(secondResult.id());
            assertThat(countRows("subscription", "package_id", servicePackage.getId())).isEqualTo(1);
            assertThat(countRows("idempotency_record", "idempotency_key", idempotencyKey)).isEqualTo(1);
            assertThat(countRows("outbox_event", "aggregate_id", firstResult.id().toString())).isEqualTo(1);
        } finally {
            start.countDown();
            for (Long subscriptionId : jdbcTemplate.queryForList(
                    "select id from subscription where package_id = ?",
                    Long.class,
                    servicePackage.getId())) {
                String aggregateId = subscriptionId.toString();
                jdbcTemplate.update(
                        "delete from processed_event where event_id in "
                                + "(select event_id from outbox_event where aggregate_id = ?)",
                        aggregateId);
                jdbcTemplate.update("delete from outbox_event where aggregate_id = ?", aggregateId);
                jdbcTemplate.update(
                        "delete from idempotency_record where subscription_id = ?", subscriptionId);
                jdbcTemplate.update("delete from subscription where id = ?", subscriptionId);
            }
            jdbcTemplate.update("delete from idempotency_record where idempotency_key = ?", idempotencyKey);
            jdbcTemplate.update("delete from idempotency_key_claim where idempotency_key = ?", idempotencyKey);
            servicePackageRepository.deleteById(servicePackage.getId());
        }
    }

    private long countRows(String table, String column, Object value) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?",
                Long.class,
                value);
    }

    @Test
    @Transactional
    void createSubscriptionRejectsReusingAnIdempotencyKeyWithDifferentPayload() throws Exception {
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("API-CONFLICT", "Conflict package", true));
        mockMvc.perform(createSubscription("api-conflict-key", "customer-original", servicePackage.getId()))
                .andExpect(status().isAccepted());

        mockMvc.perform(createSubscription("api-conflict-key", "customer-changed", servicePackage.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Idempotency-Key was already used with a different request"));

        assertThat(subscriptionRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    @Transactional
    void createSubscriptionRejectsInvalidRequestsAndUnavailablePackages() throws Exception {
        mockMvc.perform(post("/api/subscriptions")
                        .with(csrf())
                        .header("Idempotency-Key", "api-invalid-key")
                        .contentType("application/json")
                        .content("{\"customerRef\":\"\",\"packageId\":0}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/subscriptions")
                        .with(csrf())
                        .header("Idempotency-Key", "x".repeat(129))
                        .contentType("application/json")
                        .content("{\"customerRef\":\"customer-valid\",\"packageId\":1}"))
                .andExpect(status().isBadRequest());

        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("API-UNAVAILABLE", "Unavailable package", false));
        mockMvc.perform(createSubscription("api-unavailable-key", "customer-unavailable", servicePackage.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void getSubscriptionReturnsStatusOrNotFound() throws Exception {
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("API-GET", "Get package", true));
        MvcResult created = mockMvc.perform(createSubscription(
                        "api-get-key", "customer-get", servicePackage.getId()))
                .andExpect(status().isAccepted())
                .andReturn();
        String subscriptionPath = URI.create(created.getResponse().getHeader("Location")).getPath();

        mockMvc.perform(get(subscriptionPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerRef").value("customer-get"));

        mockMvc.perform(get("/api/subscriptions/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void activationConsumerProcessesEventOnceAndIgnoresRedelivery() {
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("EVENT-ACTIVATION", "Activation package", true));
        var requested = subscriptionService.requestSubscription(
                "event-activation-key", "customer-event", servicePackage.getId());
        var outboxEvent = outboxEventRepository.findAll().getFirst();
        var event = eventPayloadCodec.decode(outboxEvent.getPayload());

        assertThat(activationProcessor.process(event)).isTrue();
        assertThat(activationProcessor.process(event)).isFalse();
        assertThat(subscriptionService.getSubscription(requested.id()).status())
                .isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createSubscription(
            String idempotencyKey,
            String customerRef,
            Long packageId) {
        return post("/api/subscriptions")
                .with(csrf())
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .content("""
                        {"customerRef":"%s","packageId":%d}
                        """.formatted(customerRef, packageId));
    }
}
