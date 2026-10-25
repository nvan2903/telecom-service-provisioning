# Database design

## Implemented schema

Flyway migration `V1` creates the starter health-check table. Migration `V2` adds the
initial provisioning tables; applied migrations are append-only and should not be edited.

### `service_package`

| Column | Type | Constraints / purpose |
| --- | --- | --- |
| `id` | `BIGINT` | Identity primary key |
| `code` | `VARCHAR(50)` | Required, unique package code |
| `name` | `VARCHAR(150)` | Required display name |
| `active` | `BOOLEAN` | Required; defaults to `TRUE` |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | Required; defaults to the current time |

### `subscription`

| Column | Type | Constraints / purpose |
| --- | --- | --- |
| `id` | `BIGINT` | Identity primary key |
| `customer_ref` | `VARCHAR(100)` | Required customer reference; use synthetic values for this demo |
| `package_id` | `BIGINT` | Required foreign key to `service_package` |
| `status` | `VARCHAR(30)` | Required lifecycle status, constrained to supported values |
| `version` | `BIGINT` | Required JPA optimistic-lock version |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | Required creation time |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | Required last-update time |

The application creates subscriptions only for active packages, initializes their status
to `PENDING`, and rejects blank customer references. The database foreign key prevents a
subscription from referencing a package that does not exist.

### `idempotency_record`

Flyway migration `V3` adds request idempotency:

| Column | Type | Constraints / purpose |
| --- | --- | --- |
| `idempotency_key` | `VARCHAR(128)` | Primary key supplied by the API client |
| `request_hash` | `VARCHAR(64)` | SHA-256 fingerprint of canonical request fields |
| `subscription_id` | `BIGINT` | Unique required foreign key to `subscription` |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | Required creation time |

The subscription and its idempotency record are saved in the same PostgreSQL transaction.
Repeating a key with the same request returns the original subscription; using the key
with a different request returns a conflict.

## Planned schema

Outbox events and processed-event records are not implemented yet. Add them in later
migrations together with the publisher/consumer behavior and tests.