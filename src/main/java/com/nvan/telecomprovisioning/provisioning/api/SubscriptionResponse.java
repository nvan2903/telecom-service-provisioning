package com.nvan.telecomprovisioning.provisioning.api;

import com.nvan.telecomprovisioning.provisioning.application.SubscriptionView;
import com.nvan.telecomprovisioning.provisioning.domain.SubscriptionStatus;

import java.time.Instant;

public record SubscriptionResponse(
        Long id,
        String customerRef,
        Long packageId,
        SubscriptionStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static SubscriptionResponse from(SubscriptionView subscription) {
        return new SubscriptionResponse(
                subscription.id(),
                subscription.customerRef(),
                subscription.packageId(),
                subscription.status(),
                subscription.createdAt(),
                subscription.updatedAt());
    }
}
