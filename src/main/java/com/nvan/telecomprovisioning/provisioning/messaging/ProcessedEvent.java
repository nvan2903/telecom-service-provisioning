package com.nvan.telecomprovisioning.provisioning.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "processed_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_processed_event_consumer",
                columnNames = {"event_id", "consumer_name"}))
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID eventId, String consumerName) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.consumerName = Objects.requireNonNull(consumerName, "consumerName");
        if (consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName must not be blank");
        }
    }

    @PrePersist
    private void setProcessedAt() {
        if (processedAt == null) {
            processedAt = Instant.now();
        }
    }
}
