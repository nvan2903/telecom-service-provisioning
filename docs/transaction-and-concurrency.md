# Transactions and concurrency

## Subscription request

The application transaction stores an idempotency-key claim, subscription, idempotency
record, and outbox event together. It inserts and flushes the unique claim before writing
the subscription so simultaneous transactions using the same key serialize at the database
constraint. If the transaction fails, the claim and all associated records roll back.
After a concurrent conflict, the losing request re-reads and returns the winning result
when its payload hash matches; a different payload is a conflict.

The application test suite verifies same-key concurrent requests create one subscription,
one idempotency record, and one outbox event.

## Outbox publisher coordination

Each publisher claims ready outbox rows in a short PostgreSQL transaction using
`FOR UPDATE SKIP LOCKED`, assigns a unique claim token and lease expiry, then commits
before contacting Kafka. Other publisher instances skip rows with a live lease. Marking
published or recording a retry requires the current token and clears the claim. If a
publisher exits unexpectedly, the lease allows another instance to recover the row after
the configured `claim-duration`.

The integration suite invokes the publisher concurrently from two threads and verifies
delivery and consumer deduplication for all rows. It does not launch multiple application
processes or cover network partitions that outlast the lease. A separate test verifies an
abandoned claim becomes available after its lease expires.

## Kafka publication and consumption

Kafka publication occurs outside the subscription transaction. The outbox event remains
available until the publisher receives broker acknowledgement and records `published_at`.
A crash between acknowledgement and that database update can cause duplicate publication,
so consumers deduplicate by event ID.

The consumer stores its processed-event marker and activates the subscription in one
database transaction. Kafka listener failures receive three retries with a fixed one-second
backoff before publication to `<activation-topic>.DLT`.

The Testcontainers integration test exercises successful PostgreSQL-to-Kafka outbox
publication, activation, retry of persisted outbox state after backoff, consumer retry
exhaustion and DLT delivery. It also pauses Kafka and verifies the outbox recovers after
the broker resumes, and exercises concurrent publisher calls in one application instance.
Multi-process publisher coordination and production-scale concurrency remain unverified.
