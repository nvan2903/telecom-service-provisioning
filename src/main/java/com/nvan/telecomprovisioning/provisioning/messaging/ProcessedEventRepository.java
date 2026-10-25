package com.nvan.telecomprovisioning.provisioning.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
