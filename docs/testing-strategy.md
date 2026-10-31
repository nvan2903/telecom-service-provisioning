# Testing strategy

## Test layers

- **Application tests:** Spring Boot tests using H2 for API validation, idempotency,
  subscription state, transaction behavior, duplicate event handling, the mock provider
  boundary, and anonymous health endpoint behavior without health details.
- **Container integration tests:** `MessagingIT` uses Testcontainers PostgreSQL 18 and
  Apache Kafka 4.1.0. It verifies that a committed outbox event is published and activates
  its subscription, persisted outbox state recovers after an actual Kafka container
  interruption and unpause, concurrent publisher invocations use database claims to
  preserve all outbox events, expired publisher leases can be reclaimed, duplicate Kafka
  delivery applies the activation only once, a persisted outbox failure is retried after
  backoff, and a poison message is retried before it is routed to the DLT. The activation
  provider used in these tests is an in-memory mock, not a carrier integration.
- **CI:** GitHub Actions runs `./mvnw -B -Pintegration-tests verify` on pushes and pull
  requests targeting `integration/pre-develop`, `develop`, or `main`.

## Run tests locally

```powershell
.\mvnw.cmd test
.\mvnw.cmd -Pintegration-tests verify
```

The integration profile requires Docker to be running and able to start Linux containers.
The default test command does not start containers.

## Coverage boundaries

The container tests validate the configured local PostgreSQL/Kafka workflow, not
production availability, TLS/authentication, multi-broker behavior, or load capacity.
The mock provider's deduplication is process-local; tests do not validate durable
idempotency across provider or application restarts.
Outbox recovery test simulates an already-persisted transient failure and validates retry
after backoff while Kafka is available. A separate integration test pauses the single
Kafka test container, verifies the publish failure is persisted, then unpauses the broker
and verifies that the outbox event is subsequently published. Concurrent invocation
coverage runs the publisher from two threads and exercises PostgreSQL row claims; lease
expiry is tested by simulating an abandoned claim, not killing an application process.
The suite does not launch separate application processes. Duplicate-delivery coverage
validates consumer idempotency. Multi-broker failover and deployment-level tests remain
follow-up work. Keep test data synthetic; never use real customer data or credentials.
