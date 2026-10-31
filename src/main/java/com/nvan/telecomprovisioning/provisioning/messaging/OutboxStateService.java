package com.nvan.telecomprovisioning.provisioning.messaging;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
    public List<OutboxEvent> claimReady(int batchSize, Duration claimDuration) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (claimDuration.isNegative() || claimDuration.isZero()) {
            throw new IllegalArgumentException("claimDuration must be positive");
        }

        Instant now = Instant.now();
        UUID claimToken = UUID.randomUUID();
        List<OutboxEvent> events = outboxEventRepository.findReadyToClaim(now, batchSize);
        events.forEach(event -> event.claim(claimToken, now.plus(claimDuration)));
        return outboxEventRepository.saveAll(events);
    }

    @Transactional
    public boolean markPublished(UUID eventId, UUID claimToken) {
        OutboxEvent event = findEvent(eventId);
        if (!event.isClaimedBy(claimToken)) {
            return false;
        }
        event.markPublished(Instant.now());
        outboxEventRepository.save(event);
        return true;
    }

    @Transactional
    public boolean recordFailure(UUID eventId, UUID claimToken, String error, Instant retryAt) {
        OutboxEvent event = findEvent(eventId);
        if (!event.isClaimedBy(claimToken)) {
            return false;
        }
        event.recordFailure(error, retryAt);
        outboxEventRepository.save(event);
        return true;
    }

    private OutboxEvent findEvent(UUID eventId) {
        return outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Outbox event does not exist: " + eventId));
    }
}
