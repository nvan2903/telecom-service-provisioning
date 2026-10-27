package com.nvan.telecomprovisioning.provisioning.infrastructure.persistence;

import com.nvan.telecomprovisioning.provisioning.domain.IdempotencyKeyClaim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyClaimRepository extends JpaRepository<IdempotencyKeyClaim, String> {
}
