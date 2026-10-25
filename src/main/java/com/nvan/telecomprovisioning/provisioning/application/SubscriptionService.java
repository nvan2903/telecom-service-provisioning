package com.nvan.telecomprovisioning.provisioning.application;

import com.nvan.telecomprovisioning.provisioning.domain.ServicePackage;
import com.nvan.telecomprovisioning.provisioning.domain.Subscription;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.ServicePackageRepository;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

    private final ServicePackageRepository servicePackageRepository;
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(
            ServicePackageRepository servicePackageRepository,
            SubscriptionRepository subscriptionRepository) {
        this.servicePackageRepository = servicePackageRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public Subscription requestSubscription(String customerRef, Long packageId) {
        ServicePackage servicePackage = servicePackageRepository.findById(packageId)
                .filter(ServicePackage::isActive)
                .orElseThrow(() -> new ServicePackageUnavailableException(packageId));

        return subscriptionRepository.save(Subscription.request(customerRef, servicePackage));
    }
}
