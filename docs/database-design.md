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

### `idempotency_key_claim`

Flyway migration `V5` adds a unique claim row for each idempotency key. A request inserts
and flushes the claim before creating subscription data, in the same transaction. The
unique key serializes simultaneous requests across application instances; if the request
fails, its claim rolls back with the transaction. Successful claims are retained alongside
the corresponding `idempotency_record`.

The subscription, idempotency record, claim, and outbox event are saved in one PostgreSQL
transaction. Repeating a key with the same request returns the original subscription;
using the key with a different request returns a conflict.

## Messaging schema

Migration `V4` adds `outbox_event` and `processed_event`. Outbox records are retained for
publication/recovery and processed-event records deduplicate consumer deliveries.