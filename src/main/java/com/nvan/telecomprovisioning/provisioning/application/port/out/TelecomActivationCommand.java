package com.nvan.telecomprovisioning.provisioning.application.port.out;

import java.util.UUID;

public record TelecomActivationCommand(
        UUID eventId,
        Long subscriptionId,
        String customerRef,
        Long packageId) {
}
