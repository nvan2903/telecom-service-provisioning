package com.nvan.telecomprovisioning.provisioning.messaging;

import com.github.dockerjava.api.DockerClient;
import com.nvan.telecomprovisioning.provisioning.application.SubscriptionService;
import com.nvan.telecomprovisioning.provisioning.domain.ServicePackage;
import com.nvan.telecomprovisioning.provisioning.domain.SubscriptionStatus;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.ServicePackageRepository;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MessagingIT {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    private static final String TOPIC = "activation-it-" + UUID.randomUUID();
    private static final String GROUP_ID = "activation-it-" + UUID.randomUUID();

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18"));

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:4.1.0"));

    @DynamicPropertySource
    static void configureServices(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> GROUP_ID);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.listener.auto-startup", () -> true);
        registry.add("provisioning.messaging.activation-topic", () -> TOPIC);
        registry.add("provisioning.messaging.publish-timeout", () -> "3s");
        registry.add("provisioning.outbox.publisher.enabled", () -> true);
        registry.add("provisioning.outbox.publisher.fixed-delay", () -> "3600000");
        registry.add("provisioning.outbox.publisher.batch-size", () -> 20);
    }

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private ServicePackageRepository servicePackageRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OutboxStateService outboxStateService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private CountingEventPayloadCodec countingEventPayloadCodec;

    @Test
    void publishesOutboxEventToKafkaAndActivatesSubscription() throws Exception {
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("IT-" + UUID.randomUUID(), "Integration package", true));

        var requested = subscriptionService.requestSubscription(
                "it-" + UUID.randomUUID(), "integration-customer", servicePackage.getId());
        outboxPublisher.publishPendingEvents();

        awaitCondition(() -> subscriptionService.getSubscription(requested.id()).status()
                == SubscriptionStatus.ACTIVE);

        OutboxEvent publishedEvent = outboxEventRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(requested.id().toString()))
                .findFirst()
                .orElseThrow();
        assertThat(publishedEvent.getPublishedAt()).isNotNull();
        assertThat(publishedEvent.getAttempts()).isEqualTo(1);
    }

    @Test
    void retriesPersistedOutboxEventAfterBackoff() throws Exception {
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("RETRY-" + UUID.randomUUID(), "Retry integration package", true));
        var requested = subscriptionService.requestSubscription(
                "retry-" + UUID.randomUUID(), "retry-customer", servicePackage.getId());
        OutboxEvent event = outboxEventRepository.findAll().stream()
                .filter(candidate -> candidate.getAggregateId().equals(requested.id().toString()))
                .findFirst()
                .orElseThrow();

        UUID claimToken = UUID.randomUUID();
        event.claim(claimToken, Instant.now().plusSeconds(30));
        outboxEventRepository.saveAndFlush(event);
        outboxStateService.recordFailure(
                event.getEventId(), claimToken, "simulated temporary Kafka outage",
                Instant.now().plusMillis(500));

        assertThat(outboxStateService.findReady(20))
                .noneMatch(candidate -> candidate.getEventId().equals(event.getEventId()));
        awaitCondition(() -> outboxStateService.findReady(20).stream()
                .anyMatch(candidate -> candidate.getEventId().equals(event.getEventId())));

        outboxPublisher.publishPendingEvents();

        awaitCondition(() -> subscriptionService.getSubscription(requested.id()).status()
                == SubscriptionStatus.ACTIVE);
        OutboxEvent retriedEvent = outboxEventRepository.findById(event.getEventId()).orElseThrow();
        assertThat(retriedEvent.getPublishedAt()).isNotNull();
        assertThat(retriedEvent.getAttempts()).isEqualTo(2);
    }

    @Test
    void republishesPersistedOutboxEventAfterKafkaBrokerInterruption() throws Exception {
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("BR-" + UUID.randomUUID(),
                        "Broker outage package", true));
        var requested = subscriptionService.requestSubscription(
                "broker-restart-" + UUID.randomUUID(), "broker-restart-customer",
                servicePackage.getId());
        OutboxEvent event = outboxEventRepository.findAll().stream()
                .filter(candidate -> candidate.getAggregateId().equals(requested.id().toString()))
                .findFirst()
                .orElseThrow();

        DockerClient dockerClient = DockerClientFactory.instance().client();
        String kafkaContainerId = KAFKA.getContainerId();
        try {
            dockerClient.pauseContainerCmd(kafkaContainerId).exec();

            outboxPublisher.publishPendingEvents();

            OutboxEvent failedEvent = outboxEventRepository.findById(event.getEventId())
                    .orElseThrow();
            assertThat(failedEvent.getPublishedAt()).isNull();
            assertThat(failedEvent.getAttempts()).isEqualTo(1);
            assertThat(failedEvent.getLastError()).isNotBlank();
        } finally {
            dockerClient.unpauseContainerCmd(kafkaContainerId).exec();
        }

        awaitKafkaAvailable();
        awaitCondition(() -> outboxStateService.findReady(20).stream()
                .anyMatch(candidate -> candidate.getEventId().equals(event.getEventId())));

        outboxPublisher.publishPendingEvents();

        awaitCondition(() -> subscriptionService.getSubscription(requested.id()).status()
                == SubscriptionStatus.ACTIVE);
        OutboxEvent recoveredEvent = outboxEventRepository.findById(event.getEventId())
                .orElseThrow();
        assertThat(recoveredEvent.getPublishedAt()).isNotNull();
        assertThat(recoveredEvent.getAttempts()).isEqualTo(2);
    }

    @Test
    void concurrentPublishersDoNotLoseOutboxEvents() throws Exception {
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("CP-" + UUID.randomUUID(), "Concurrent publisher package", true));
        List<Long> subscriptionIds = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            var requested = subscriptionService.requestSubscription(
                    "concurrent-publisher-" + UUID.randomUUID(),
                    "concurrent-publisher-customer-" + index, servicePackage.getId());
            subscriptionIds.add(requested.id());
        }
        List<OutboxEvent> events = outboxEventRepository.findAll().stream()
                .filter(event -> subscriptionIds.contains(Long.valueOf(event.getAggregateId())))
                .toList();
        assertThat(events).hasSize(subscriptionIds.size());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var publisher = (Runnable) () -> {
                ready.countDown();
                try {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start concurrent publisher");
                    }
                    outboxPublisher.publishPendingEvents();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Concurrent publisher was interrupted", exception);
                }
            };
            var firstPublisher = executor.submit(publisher);
            var secondPublisher = executor.submit(publisher);

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstPublisher.get(30, TimeUnit.SECONDS);
            secondPublisher.get(30, TimeUnit.SECONDS);
        }

        awaitCondition(() -> subscriptionIds.stream()
                .allMatch(id -> subscriptionService.getSubscription(id).status()
                        == SubscriptionStatus.ACTIVE));

        for (OutboxEvent event : events) {
            OutboxEvent publishedEvent = outboxEventRepository.findById(event.getEventId())
                    .orElseThrow();
            assertThat(publishedEvent.getPublishedAt()).isNotNull();
            assertThat(publishedEvent.getAttempts()).isEqualTo(1);
            assertThat(processedEventRepository.countByEventIdAndConsumerName(
                    event.getEventId(), "subscription-activation"))
                    .isEqualTo(1);
        }
    }

    @Test
    void reclaimsOutboxEventAfterPublisherLeaseExpires() throws Exception {
        outboxPublisher.publishPendingEvents();
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("LEASE-" + UUID.randomUUID(), "Lease recovery package", true));
        var requested = subscriptionService.requestSubscription(
                "lease-recovery-" + UUID.randomUUID(), "lease-recovery-customer",
                servicePackage.getId());
        OutboxEvent event = outboxEventRepository.findAll().stream()
                .filter(candidate -> candidate.getAggregateId().equals(requested.id().toString()))
                .findFirst()
                .orElseThrow();

        List<OutboxEvent> firstClaim = outboxStateService.claimReady(1, Duration.ofSeconds(3));
        assertThat(firstClaim).hasSize(1);
        assertThat(firstClaim.getFirst().getEventId()).isEqualTo(event.getEventId());
        assertThat(outboxStateService.findReady(20))
                .noneMatch(candidate -> candidate.getEventId().equals(event.getEventId()));

        Thread.sleep(3_100);
        outboxPublisher.publishPendingEvents();

        awaitCondition(() -> subscriptionService.getSubscription(requested.id()).status()
                == SubscriptionStatus.ACTIVE);
        OutboxEvent publishedEvent = outboxEventRepository.findById(event.getEventId())
                .orElseThrow();
        assertThat(publishedEvent.getPublishedAt()).isNotNull();
        assertThat(publishedEvent.getClaimToken()).isNull();
        assertThat(publishedEvent.getClaimedUntil()).isNull();
    }

    @RepeatedTest(3)
    void duplicateKafkaDeliveryActivatesSubscriptionOnlyOnce() throws Exception {
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("DUPLICATE-" + UUID.randomUUID(),
                        "Duplicate delivery package", true));
        var requested = subscriptionService.requestSubscription(
                "duplicate-" + UUID.randomUUID(), "duplicate-customer", servicePackage.getId());
        OutboxEvent event = outboxEventRepository.findAll().stream()
                .filter(candidate -> candidate.getAggregateId().equals(requested.id().toString()))
                .findFirst()
                .orElseThrow();
        int decodeAttemptsBefore = countingEventPayloadCodec.getDecodeAttempts();

        outboxPublisher.publishPendingEvents();
        awaitCondition(() -> subscriptionService.getSubscription(requested.id()).status()
                == SubscriptionStatus.ACTIVE);

        kafkaTemplate.send(TOPIC, event.getAggregateId(), event.getPayload())
                .get(10, TimeUnit.SECONDS);
        awaitCondition(() -> countingEventPayloadCodec.getDecodeAttempts()
                >= decodeAttemptsBefore + 2);

        assertThat(processedEventRepository.countByEventIdAndConsumerName(
                event.getEventId(), "subscription-activation"))
                .isEqualTo(1);
        assertThat(subscriptionService.getSubscription(requested.id()).status())
                .isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void retriesPoisonMessageAndPublishesItToDeadLetterTopic() throws Exception {
        String deadLetterTopic = TOPIC + ".DLT";
        int attemptsBefore = countingEventPayloadCodec.getDecodeAttempts();

        kafkaTemplate.send(TOPIC, "poison-key", "{}").get(10, TimeUnit.SECONDS);
        awaitCondition(() -> countingEventPayloadCodec.getDecodeAttempts() - attemptsBefore >= 4);
        assertThat(countingEventPayloadCodec.getDecodeAttempts() - attemptsBefore).isEqualTo(4);

        ConsumerRecord<String, String> deadLetterRecord = pollDeadLetterRecord(deadLetterTopic);

        assertThat(deadLetterRecord.value()).isEqualTo("{}");
    }

    private ConsumerRecord<String, String> pollDeadLetterRecord(String topic) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                var records = consumer.poll(Duration.ofMillis(500));
                if (!records.isEmpty()) {
                    return records.iterator().next();
                }
            }
        }
        throw new AssertionError("Timed out waiting for dead-letter record on " + topic);
    }

    private void awaitCondition(CheckedBooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private void awaitKafkaAvailable() throws Exception {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 1_000);
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 20_000);

        try (Admin admin = Admin.create(properties)) {
            try {
                admin.describeCluster().nodes().get(20, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException exception) {
                throw new AssertionError("Kafka did not recover after container unpause", exception);
            }
        }
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }

    @TestConfiguration
    static class CodecTestConfiguration {

        @Bean
        @Primary
        CountingEventPayloadCodec countingEventPayloadCodec() {
            return new CountingEventPayloadCodec();
        }
    }

    static class CountingEventPayloadCodec extends EventPayloadCodec {

        private final AtomicInteger decodeAttempts = new AtomicInteger();

        @Override
        public SubscriptionRequestedEvent decode(String payload) {
            decodeAttempts.incrementAndGet();
            return super.decode(payload);
        }

        int getDecodeAttempts() {
            return decodeAttempts.get();
        }
    }
}
