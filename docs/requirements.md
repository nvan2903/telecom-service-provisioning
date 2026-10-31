# MVP requirements

## Functional requirements

| ID | Requirement | Status |
|---|---|---|
| FR-01 | List active service packages | Implemented |
| FR-02 | Accept a subscription request and return its status resource | Implemented |
| FR-03 | Replay requests safely with an idempotency key; reject key reuse with a different payload | Implemented |
| FR-04 | Persist the subscription and outbox event atomically | Implemented |
| FR-05 | Publish outbox events and process activation asynchronously through Kafka | Implemented |
| FR-06 | Ignore duplicate event delivery without repeating the state transition | Implemented |
| FR-07 | Retry transient consumer failures a bounded number of times | Implemented |
| FR-08 | Route exhausted consumer failures to a dead-letter topic | Implemented |
| FR-09 | Query subscription status | Implemented |
| FR-10 | Trace requests/events with operational identifiers and avoid logging secrets | Proposed |
| FR-11 | Submit activation through an idempotent provider boundary | Implemented for local mock |

The local `mock` provider accepts activation requests in memory using `eventId` as its
idempotency key. It is intentionally not durable and does not connect to a real telecom
network or external activation provider. A production provider adapter must meet its
provider's durable idempotency contract before deployment.

## Non-functional requirements

- Use database transactions and constraints for local consistency; do not claim a
  distributed transaction across PostgreSQL and Kafka.
- Keep API input validation and event processing idempotent.
- Keep local setup reproducible through Docker Compose and document test commands.
- Use synthetic data and keep credentials out of version control.
- Treat local Compose security and single-broker Kafka as development-only.
