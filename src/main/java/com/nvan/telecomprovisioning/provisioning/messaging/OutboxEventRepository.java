package com.nvan.telecomprovisioning.provisioning.messaging;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
            select event from OutboxEvent event
            where event.publishedAt is null
              and event.nextAttemptAt <= :now
              and (event.claimedUntil is null or event.claimedUntil <= :now)
            order by event.createdAt, event.eventId
            """)
    List<OutboxEvent> findReadyToPublish(@Param("now") Instant now, Pageable pageable);

    @Query(value = """
            select * from outbox_event
            where published_at is null
              and next_attempt_at <= :now
              and (claimed_until is null or claimed_until <= :now)
            order by created_at, event_id
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> findReadyToClaim(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize);
}
