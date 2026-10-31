ALTER TABLE outbox_event
    ADD COLUMN claim_token UUID;

ALTER TABLE outbox_event
    ADD COLUMN claimed_until TIMESTAMP WITH TIME ZONE;

ALTER TABLE outbox_event
    ADD CONSTRAINT ck_outbox_event_claim_pair CHECK (
        (claim_token IS NULL AND claimed_until IS NULL)
        OR (claim_token IS NOT NULL AND claimed_until IS NOT NULL)
    );

CREATE INDEX idx_outbox_event_claimable
    ON outbox_event (published_at, next_attempt_at, claimed_until, created_at, event_id);
