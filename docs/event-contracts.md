# Event contracts

## `SubscriptionRequested` (schema version 1)

Topic: `subscription-activation-requested` (configurable with `ACTIVATION_TOPIC`).
The Kafka record key is the subscription ID, preserving order for messages concerning the
same subscription.

```json
{
  "eventId": "83ae9f13-ccba-48a2-81b4-71fddf38f0fe",
  "eventType": "SubscriptionRequested",
  "schemaVersion": 1,
  "occurredAt": "2026-10-25T16:00:00Z",
  "subscriptionId": 42,
  "customerRef": "demo-customer-1",
  "packageId": 1
}
```

The API creates the event ID once and persists the JSON payload in `outbox_event` in the
same PostgreSQL transaction as its `subscription` and `idempotency_record`. The outbox
publisher sends the stored payload and marks the row published only after Kafka
acknowledges it. A crash after Kafka acknowledgement but before the database update can
cause a duplicate publish; consumers must use `eventId` for idempotency.
If Kafka is unavailable, the publisher records the failure and schedules a retry. The
integration suite pauses and unpauses its Kafka container to verify the persisted outbox
event is published after the broker resumes service. Publishers claim rows with short
PostgreSQL transactions and expiring leases so concurrent instances normally work on
different rows; an expired claim or a crash after Kafka acknowledgement can still cause
redelivery.

The activation consumer validates event type and schema version, then inserts
`processed_event(event_id, consumer_name)` and moves the associated subscription from
`PENDING` to `ACTIVE` in the same database transaction. Repeated delivery of an already
processed event is acknowledged without applying the state change again. Activation is
simulated; no external telecom network is called. The integration suite verifies repeated
delivery of the same event does not apply activation more than once.

## Retries and dead-letter topic

Consumer failures are retried three times with a one-second fixed backoff. Exhausted
messages are sent to `subscription-activation-requested.DLT` with Spring Kafka diagnostic
headers. The local broker auto-creates topics; production deployments should provision
the main and DLT topics explicitly with appropriate partitions, retention, replication,
and access controls.