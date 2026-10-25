package com.nvan.telecomprovisioning.provisioning.application;

public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException(Long subscriptionId) {
        super("Subscription was not found: " + subscriptionId);
    }
}
