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
exhaustion, and DLT delivery. It simulates a recorded temporary outbox failure while Kafka
is available; an actual broker outage/restart, multiple publisher instances, and
production-scale concurrency remain unverified.
