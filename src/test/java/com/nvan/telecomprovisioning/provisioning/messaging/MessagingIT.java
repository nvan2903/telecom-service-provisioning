package com.nvan.telecomprovisioning.provisioning.messaging;

import com.nvan.telecomprovisioning.provisioning.application.SubscriptionService;
import com.nvan.telecomprovisioning.provisioning.domain.ServicePackage;
import com.nvan.telecomprovisioning.provisioning.domain.SubscriptionStatus;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.ServicePackageRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
        registry.add("provisioning.outbox.publisher.enabled", () -> true);
        registry.add("provisioning.outbox.publisher.fixed-delay", () -> "100ms");
        registry.add("provisioning.outbox.publisher.batch-size", () -> 20);
    }

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private ServicePackageRepository servicePackageRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

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
