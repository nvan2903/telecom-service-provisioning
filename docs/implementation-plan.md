# Implementation plan

## Current milestone status

| Capability | Status | Evidence / notes |
|---|---|---|
| Spring Boot project and local Compose app, PostgreSQL, and Kafka | Implemented for local demo | `Dockerfile`, `compose.yaml`, application configuration |
| Package listing and subscription create/status API | Implemented | API controllers and application tests |
| Subscription idempotency and atomic outbox persistence | Implemented | Transaction service, unique database constraints, application tests |
| Kafka outbox publication and activation consumer | Implemented | Scheduled publisher, listener, and integration test |
| Duplicate event handling | Implemented | Processed-event table and application test |
| Telecom provider boundary and local mock | Implemented for local demo | `TelecomActivationGateway`, in-memory mock; no carrier integration |
| Consumer retry and dead-letter routing | Implemented | Kafka error handler and PostgreSQL/Kafka integration test |
| CI verification | Implemented | `.github/workflows/maven.yml` |
| Production deployment and real telecom activation | Deferred | Outside the local MVP scope |

## Next work

1. Validate lease recovery after a real application process crash and tune claim duration
   for deployment timeouts. Integration coverage verifies expired claims can be reclaimed
   and exercises PostgreSQL claims under concurrent invocations, but these share one
   application instance.
2. Select the real telecom provider and its durable idempotency contract.
3. Select identity, secrets, TLS, monitoring, and deployment targets before production
   hardening; current Compose is loopback-only local demo infrastructure.

## Verification commands

- `.\mvnw.cmd test` — fast H2-backed application tests.
- `.\mvnw.cmd -Pintegration-tests verify` — unit tests plus PostgreSQL/Kafka Testcontainers
  integration tests; requires Docker.
