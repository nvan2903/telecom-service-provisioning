package com.nvan.telecomprovisioning.provisioning.infrastructure.persistence;

import com.nvan.telecomprovisioning.provisioning.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
}
