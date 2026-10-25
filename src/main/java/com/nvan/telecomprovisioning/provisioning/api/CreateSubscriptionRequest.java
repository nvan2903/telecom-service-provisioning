package com.nvan.telecomprovisioning.provisioning.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateSubscriptionRequest(
        @NotBlank @Size(max = 100) String customerRef,
        @NotNull @Positive Long packageId) {
}
