package com.nvan.telecomprovisioning.provisioning.application.port.out;

public interface TelecomActivationGateway {

    void activate(TelecomActivationCommand command);
}
