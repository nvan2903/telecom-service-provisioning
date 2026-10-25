package com.nvan.telecomprovisioning.provisioning.messaging;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxStateService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxStateService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> findReady(int batchSize) {
        return outboxEventRepository.findReadyToPublish(Instant.now(), PageRequest.of(0, batchSize));
    }

    @Transactional
    public void markPublished(UUID eventId) {
        OutboxEvent event = findEvent(eventId);
        event.markPublished(Instant.now());
        outboxEventRepository.save(event);
    }

    @Transactional
    public void recordFailure(UUID eventId, String error, Instant retryAt) {
        OutboxEvent event = findEvent(eventId);
        event.recordFailure(error, retryAt);
        outboxEventRepository.save(event);
    }

    private OutboxEvent findEvent(UUID eventId) {
        return outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Outbox event does not exist: " + eventId));
    }
}
