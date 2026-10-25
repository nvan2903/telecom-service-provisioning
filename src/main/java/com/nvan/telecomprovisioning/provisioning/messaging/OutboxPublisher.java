package com.nvan.telecomprovisioning.provisioning.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "provisioning.outbox.publisher", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxStateService outboxStateService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final Duration publishTimeout;
    private final int batchSize;

    public OutboxPublisher(
            OutboxStateService outboxStateService,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${provisioning.messaging.activation-topic}") String topic,
            @Value("${provisioning.messaging.publish-timeout}") Duration publishTimeout,
            @Value("${provisioning.outbox.publisher.batch-size}") int batchSize) {
        this.outboxStateService = outboxStateService;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishTimeout = publishTimeout;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${provisioning.outbox.publisher.fixed-delay}")
    public void publishPendingEvents() {
        for (OutboxEvent event : outboxStateService.findReady(batchSize)) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            outboxStateService.markPublished(event.getEventId());
        } catch (Exception exception) {
            int nextAttempt = event.getAttempts() + 1;
            long delaySeconds = Math.min(60L, 1L << Math.min(nextAttempt, 6));
            String error = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            outboxStateService.recordFailure(
                    event.getEventId(), error, Instant.now().plusSeconds(delaySeconds));
            logger.error("Failed to publish outbox event {} for aggregate {} (attempt {})",
                    event.getEventId(), event.getAggregateId(), nextAttempt, exception);
        }
    }
}
