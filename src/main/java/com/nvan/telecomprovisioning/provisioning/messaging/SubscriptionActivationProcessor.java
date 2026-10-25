package com.nvan.telecomprovisioning.provisioning.messaging;

import com.nvan.telecomprovisioning.provisioning.domain.Subscription;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionActivationProcessor {

    private final ProcessedEventRepository processedEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final String consumerName;

    public SubscriptionActivationProcessor(
            ProcessedEventRepository processedEventRepository,
            SubscriptionRepository subscriptionRepository,
            @Value("${provisioning.messaging.consumer-name}") String consumerName) {
        this.processedEventRepository = processedEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.consumerName = consumerName;
    }

    @Transactional
    public boolean process(SubscriptionRequestedEvent event) {
        event.validate();
        if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), consumerName)) {
            return false;
        }

        Subscription subscription = subscriptionRepository.findById(event.subscriptionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Subscription does not exist for event " + event.eventId()));

        if (!subscription.getCustomerRef().equals(event.customerRef())
                || !subscription.getServicePackage().getId().equals(event.packageId())) {
            throw new IllegalArgumentException("Event does not match its subscription");
        }

        processedEventRepository.save(new ProcessedEvent(event.eventId(), consumerName));
        subscription.activate();
        return true;
    }
}
