# Implementation plan

## Current milestone status

| Capability | Status | Evidence / notes |
|---|---|---|
| Spring Boot project and local PostgreSQL/Kafka Compose services | Implemented | `compose.yaml`, application configuration |
| Package listing and subscription create/status API | Implemented | API controllers and application tests |
| Subscription idempotency and atomic outbox persistence | Implemented | Transaction service, unique database constraints, application tests |
| Kafka outbox publication and activation consumer | Implemented | Scheduled publisher, listener, and integration test |
| Duplicate event handling | Implemented | Processed-event table and application test |
| Consumer retry and dead-letter routing | Implemented | Kafka error handler and PostgreSQL/Kafka integration test |
| CI verification | Implemented | `.github/workflows/maven.yml` |
| Production deployment and real telecom activation | Deferred | Outside the local MVP scope |

## Next work

1. Extend the outbox integration tests to cover a real broker interruption/restart and
   multiple concurrent publisher instances. The current tests verify persisted retry after
   backoff and consumer idempotency under duplicate Kafka delivery, but do not stop or
   restart Kafka or coordinate multiple publishers.
2. Assess security, observability, and deployment requirements before selecting production
   infrastructure.

## Verification commands

- `.\mvnw.cmd test` — fast H2-backed application tests.
- `.\mvnw.cmd -Pintegration-tests verify` — unit tests plus PostgreSQL/Kafka Testcontainers
  integration tests; requires Docker.
