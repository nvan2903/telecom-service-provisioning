package com.nvan.telecomprovisioning.provisioning.infrastructure.telecom;

import com.nvan.telecomprovisioning.provisioning.application.port.out.TelecomActivationCommand;
import com.nvan.telecomprovisioning.provisioning.application.port.out.TelecomActivationGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "provisioning.telecom", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockTelecomActivationGateway implements TelecomActivationGateway {

    private final Set<UUID> acceptedEventIds = ConcurrentHashMap.newKeySet();

    @Override
    public void activate(TelecomActivationCommand command) {
        acceptedEventIds.add(command.eventId());
    }

    public int acceptedRequestCount() {
        return acceptedEventIds.size();
    }
}
