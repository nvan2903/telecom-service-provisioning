package com.nvan.telecomprovisioning.provisioning.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 8000)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "claim_token")
    private UUID claimToken;

    @Column(name = "claimed_until")
    private Instant claimedUntil;

    @Version
    @Column(nullable = false)
    private long version;

    protected OutboxEvent() {
    }

    private OutboxEvent(UUID eventId, String aggregateType, String aggregateId,
                        String eventType, String payload) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.aggregateType = requireText(aggregateType, "aggregateType");
        this.aggregateId = requireText(aggregateId, "aggregateId");
        this.eventType = requireText(eventType, "eventType");
        this.payload = requireText(payload, "payload");
        this.nextAttemptAt = Instant.now();
    }

    public static OutboxEvent create(UUID eventId, String aggregateType, String aggregateId,
                                     String eventType, String payload) {
        return new OutboxEvent(eventId, aggregateType, aggregateId, eventType, payload);
    }

    public void markPublished(Instant publishedAt) {
        if (this.publishedAt == null) {
            this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
            this.attempts++;
            this.lastError = null;
            clearClaim();
        }
    }

    public void recordFailure(String error, Instant retryAt) {
        attempts++;
        lastError = truncate(requireText(error, "error"), 2000);
        nextAttemptAt = Objects.requireNonNull(retryAt, "retryAt");
        clearClaim();
    }

    public void claim(UUID claimToken, Instant claimedUntil) {
        this.claimToken = Objects.requireNonNull(claimToken, "claimToken");
        this.claimedUntil = Objects.requireNonNull(claimedUntil, "claimedUntil");
    }

    public boolean isClaimedBy(UUID claimToken) {
        return this.claimToken != null && this.claimToken.equals(claimToken);
    }

    private void clearClaim() {
        claimToken = null;
        claimedUntil = null;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public UUID getClaimToken() {
        return claimToken;
    }

    public Instant getClaimedUntil() {
        return claimedUntil;
    }

    @PrePersist
    private void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
