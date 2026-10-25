package com.nvan.telecomprovisioning.provisioning.messaging;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionRequestedEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String occurredAt,
        Long subscriptionId,
        String customerRef,
        Long packageId) {

    public static final String TYPE = "SubscriptionRequested";
    public static final int SCHEMA_VERSION = 1;

    public void validate() {
        if (eventId == null
                || !TYPE.equals(eventType)
                || schemaVersion != SCHEMA_VERSION
                || occurredAt == null
                || subscriptionId == null
                || subscriptionId <= 0
                || customerRef == null
                || customerRef.isBlank()
                || packageId == null
                || packageId <= 0) {
            throw new IllegalArgumentException("SubscriptionRequested event is invalid");
        }
        try {
            Instant.parse(occurredAt);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("SubscriptionRequested event timestamp is invalid", exception);
        }
    }
}
