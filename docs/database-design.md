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

## Planned schema

Idempotency records, outbox events, and processed-event records are not implemented yet.
Add them in later migrations when their application behavior and tests are introduced.