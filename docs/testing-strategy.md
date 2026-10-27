# Testing strategy

## Test layers

- **Application tests:** Spring Boot tests using H2 for API validation, idempotency,
  subscription state, transaction behavior, and duplicate event handling.
- **Container integration tests:** `MessagingIT` uses Testcontainers PostgreSQL 18 and
  Apache Kafka 4.1.0. It verifies that a committed outbox event is published and activates
  its subscription, that a persisted outbox failure is retried after backoff, and that a
  poison message is retried before it is routed to the DLT.
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
Outbox recovery test simulates an already-persisted transient failure and validates retry
after backoff while Kafka is available; an actual broker outage/restart and deployment-level
tests remain follow-up work. Keep test data synthetic; never use real customer data or
credentials.
