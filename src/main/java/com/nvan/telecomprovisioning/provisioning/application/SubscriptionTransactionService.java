package com.nvan.telecomprovisioning.provisioning.application;

import com.nvan.telecomprovisioning.provisioning.domain.IdempotencyRecord;
import com.nvan.telecomprovisioning.provisioning.domain.ServicePackage;
import com.nvan.telecomprovisioning.provisioning.domain.Subscription;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.IdempotencyRecordRepository;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.ServicePackageRepository;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.SubscriptionRepository;
import com.nvan.telecomprovisioning.provisioning.messaging.OutboxEvent;
import com.nvan.telecomprovisioning.provisioning.messaging.OutboxEventRepository;
import com.nvan.telecomprovisioning.provisioning.messaging.SubscriptionRequestedEvent;
import com.nvan.telecomprovisioning.provisioning.messaging.EventPayloadCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

@Service
public class SubscriptionTransactionService {

    private final ServicePackageRepository servicePackageRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final EventPayloadCodec eventPayloadCodec;

    public SubscriptionTransactionService(
            ServicePackageRepository servicePackageRepository,
            SubscriptionRepository subscriptionRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            OutboxEventRepository outboxEventRepository,
            EventPayloadCodec eventPayloadCodec) {
        this.servicePackageRepository = servicePackageRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.eventPayloadCodec = eventPayloadCodec;
    }

    @Transactional
    public SubscriptionView requestSubscription(String idempotencyKey, String requestHash,
                                                String customerRef, Long packageId) {
        Optional<SubscriptionView> existingSubscription = findByKeyAndVerifyRequest(idempotencyKey, requestHash);
        if (existingSubscription.isPresent()) {
            return existingSubscription.get();
        }

        ServicePackage servicePackage = servicePackageRepository.findById(packageId)
                .filter(ServicePackage::isActive)
                .orElseThrow(() -> new ServicePackageUnavailableException(packageId));

        Subscription subscription = subscriptionRepository.save(Subscription.request(customerRef, servicePackage));
        idempotencyRecordRepository.save(new IdempotencyRecord(idempotencyKey, requestHash, subscription));
        UUID eventId = UUID.randomUUID();
        SubscriptionRequestedEvent event = new SubscriptionRequestedEvent(
                eventId,
                SubscriptionRequestedEvent.TYPE,
                SubscriptionRequestedEvent.SCHEMA_VERSION,
                Instant.now().toString(),
                subscription.getId(),
                subscription.getCustomerRef(),
                servicePackage.getId());
        outboxEventRepository.save(OutboxEvent.create(
                eventId,
                "Subscription",
                subscription.getId().toString(),
                event.eventType(),
                eventPayloadCodec.encode(event)));
        return toView(subscription);
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionView> findByIdempotencyKey(String idempotencyKey, String requestHash) {
        return findByKeyAndVerifyRequest(idempotencyKey, requestHash);
    }

    @Transactional(readOnly = true)
    public SubscriptionView getSubscription(Long subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .map(this::toView)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));
    }

    private Optional<SubscriptionView> findByKeyAndVerifyRequest(String idempotencyKey, String requestHash) {
        return idempotencyRecordRepository.findById(idempotencyKey)
                .map(record -> {
                    if (!record.getRequestHash().equals(requestHash)) {
                        throw new IdempotencyConflictException();
                    }
                    return toView(record.getSubscription());
                });
    }

    private SubscriptionView toView(Subscription subscription) {
        return new SubscriptionView(
                subscription.getId(),
                subscription.getCustomerRef(),
                subscription.getServicePackage().getId(),
                subscription.getStatus(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt());
    }
}
