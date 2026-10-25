package com.nvan.telecomprovisioning.provisioning.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionActivationListener {

    private final EventPayloadCodec eventPayloadCodec;
    private final SubscriptionActivationProcessor activationProcessor;

    public SubscriptionActivationListener(
            EventPayloadCodec eventPayloadCodec,
            SubscriptionActivationProcessor activationProcessor) {
        this.eventPayloadCodec = eventPayloadCodec;
        this.activationProcessor = activationProcessor;
    }

    @KafkaListener(
            topics = "${provisioning.messaging.activation-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onSubscriptionRequested(String payload) {
        activationProcessor.process(eventPayloadCodec.decode(payload));
    }
}
