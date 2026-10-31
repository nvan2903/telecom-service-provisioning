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
import java.util.List;
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
    private final Duration claimDuration;
    private final int batchSize;

    public OutboxPublisher(
            OutboxStateService outboxStateService,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${provisioning.messaging.activation-topic}") String topic,
            @Value("${provisioning.messaging.publish-timeout}") Duration publishTimeout,
            @Value("${provisioning.outbox.publisher.claim-duration}") Duration claimDuration,
            @Value("${provisioning.outbox.publisher.batch-size}") int batchSize) {
        this.outboxStateService = outboxStateService;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishTimeout = publishTimeout;
        this.claimDuration = claimDuration;
        this.batchSize = batchSize;
        if (claimDuration.compareTo(publishTimeout) <= 0) {
            throw new IllegalArgumentException(
                    "Outbox claim duration must exceed the Kafka publish timeout");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("Outbox publisher batch size must be positive");
        }
    }

    @Scheduled(fixedDelayString = "${provisioning.outbox.publisher.fixed-delay}")
    public void publishPendingEvents() {
        for (int processed = 0; processed < batchSize; processed++) {
            List<OutboxEvent> claimedEvents = outboxStateService.claimReady(1, claimDuration);
            if (claimedEvents.isEmpty()) {
                return;
            }
            OutboxEvent event = claimedEvents.get(0);
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!outboxStateService.markPublished(event.getEventId(), event.getClaimToken())) {
                logger.warn("Outbox claim for event {} expired before it could be marked published",
                        event.getEventId());
            }
        } catch (Exception exception) {
            int nextAttempt = event.getAttempts() + 1;
            long delaySeconds = Math.min(60L, 1L << Math.min(nextAttempt, 6));
            String error = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            boolean recorded = outboxStateService.recordFailure(
                    event.getEventId(), event.getClaimToken(), error,
                    Instant.now().plusSeconds(delaySeconds));
            if (!recorded) {
                logger.warn("Outbox claim for failed event {} is no longer owned by this publisher",
                        event.getEventId());
            }
            logger.error("Failed to publish outbox event {} for aggregate {} (attempt {})",
                    event.getEventId(), event.getAggregateId(), nextAttempt, exception);
        }
    }
}
