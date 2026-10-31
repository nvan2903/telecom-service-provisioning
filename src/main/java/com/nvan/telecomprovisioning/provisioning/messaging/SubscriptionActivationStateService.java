package com.nvan.telecomprovisioning.provisioning.messaging;

import com.nvan.telecomprovisioning.provisioning.application.port.out.TelecomActivationCommand;
import com.nvan.telecomprovisioning.provisioning.domain.Subscription;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SubscriptionActivationStateService {

    private final ProcessedEventRepository processedEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final String consumerName;

    public SubscriptionActivationStateService(
            ProcessedEventRepository processedEventRepository,
            SubscriptionRepository subscriptionRepository,
            @Value("${provisioning.messaging.consumer-name}") String consumerName) {
        this.processedEventRepository = processedEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.consumerName = consumerName;
    }

    @Transactional(readOnly = true)
    public Optional<TelecomActivationCommand> prepare(SubscriptionRequestedEvent event) {
        if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), consumerName)) {
            return Optional.empty();
        }

        Subscription subscription = findAndValidateSubscription(event);
        return Optional.of(new TelecomActivationCommand(
                event.eventId(),
                subscription.getId(),
                subscription.getCustomerRef(),
                subscription.getServicePackage().getId()));
    }

    @Transactional
    public boolean markActivated(SubscriptionRequestedEvent event) {
        if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), consumerName)) {
            return false;
        }

        Subscription subscription = findAndValidateSubscription(event);
        processedEventRepository.save(new ProcessedEvent(event.eventId(), consumerName));
        subscription.activate();
        return true;
    }

    private Subscription findAndValidateSubscription(SubscriptionRequestedEvent event) {
        Subscription subscription = subscriptionRepository.findById(event.subscriptionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Subscription does not exist for event " + event.eventId()));

        if (!subscription.getCustomerRef().equals(event.customerRef())
                || !subscription.getServicePackage().getId().equals(event.packageId())) {
            throw new IllegalArgumentException("Event does not match its subscription");
        }
        return subscription;
    }
}
