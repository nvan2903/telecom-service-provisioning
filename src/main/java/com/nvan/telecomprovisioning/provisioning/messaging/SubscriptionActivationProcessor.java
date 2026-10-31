package com.nvan.telecomprovisioning.provisioning.messaging;

import com.nvan.telecomprovisioning.provisioning.application.port.out.TelecomActivationCommand;
import com.nvan.telecomprovisioning.provisioning.application.port.out.TelecomActivationGateway;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionActivationProcessor {

    private final SubscriptionActivationStateService activationStateService;
    private final TelecomActivationGateway telecomActivationGateway;

    public SubscriptionActivationProcessor(
            SubscriptionActivationStateService activationStateService,
            TelecomActivationGateway telecomActivationGateway) {
        this.activationStateService = activationStateService;
        this.telecomActivationGateway = telecomActivationGateway;
    }

    public boolean process(SubscriptionRequestedEvent event) {
        event.validate();
        var command = activationStateService.prepare(event);
        if (command.isEmpty()) {
            return false;
        }

        TelecomActivationCommand activationCommand = command.orElseThrow();
        telecomActivationGateway.activate(activationCommand);
        return activationStateService.markActivated(event);
    }
}
