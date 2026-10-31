package com.nvan.telecomprovisioning.provisioning.infrastructure.persistence;

import com.nvan.telecomprovisioning.provisioning.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
}
