package com.nvan.telecomprovisioning.provisioning.application;

import com.nvan.telecomprovisioning.provisioning.domain.SubscriptionStatus;

import java.time.Instant;

public record SubscriptionView(
        Long id,
        String customerRef,
        Long packageId,
        SubscriptionStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
