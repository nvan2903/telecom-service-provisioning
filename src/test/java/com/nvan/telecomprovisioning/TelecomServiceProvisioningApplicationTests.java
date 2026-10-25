package com.nvan.telecomprovisioning;

import com.nvan.telecomprovisioning.provisioning.application.ServicePackageUnavailableException;
import com.nvan.telecomprovisioning.provisioning.application.SubscriptionService;
import com.nvan.telecomprovisioning.provisioning.domain.ServicePackage;
import com.nvan.telecomprovisioning.provisioning.domain.SubscriptionStatus;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.ServicePackageRepository;
import com.nvan.telecomprovisioning.provisioning.infrastructure.persistence.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TelecomServiceProvisioningApplicationTests {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private ServicePackageRepository servicePackageRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Test
    void contextLoads() {
    }

    @Test
    @Transactional
    void requestSubscriptionStartsAsPendingForAnActivePackage() {
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("TEST-ACTIVE", "Active test package", true));

        var subscription = subscriptionService.requestSubscription("customer-1", servicePackage.getId());

        assertThat(subscription.getId()).isNotNull();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(subscription.getCreatedAt()).isNotNull();
        assertThat(subscription.getUpdatedAt()).isNotNull();
    }

    @Test
    @Transactional
    void requestSubscriptionRejectsAnInactivePackage() {
        ServicePackage servicePackage = servicePackageRepository.save(
                new ServicePackage("TEST-INACTIVE", "Inactive test package", false));

        assertThatThrownBy(() -> subscriptionService.requestSubscription("customer-2", servicePackage.getId()))
                .isInstanceOf(ServicePackageUnavailableException.class);
        assertThat(subscriptionRepository.count()).isZero();
    }
}
