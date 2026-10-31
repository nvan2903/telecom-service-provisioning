# REST API (current implementation)

The API persists the subscription, idempotency record, and outbox event atomically. Kafka
publishing and activation happen asynchronously. A newly created subscription initially
remains `PENDING`, so `POST /api/subscriptions` returns `202 Accepted`.
In the local Compose setup, a `mock` telecom provider accepts the activation request and
the subscription later becomes `ACTIVE`; this status demonstrates the workflow only and
does not mean a real carrier service was provisioned.

All endpoints currently use Spring Security defaults and require authentication. During
local development Spring prints the generated `user` password on startup; POST requests
also require a valid CSRF token. Configure explicit credentials and security policy before
deployment; do not use generated development credentials outside local development.

## List active packages

`GET /api/service-packages`

Returns active service packages ordered by ID.

```json
[
  {
    "id": 1,
    "code": "MOBILE-10",
    "name": "Mobile 10 GB"
  }
]
```

## Create a subscription

`POST /api/subscriptions`

Required header: `Idempotency-Key` (1–128 non-blank characters).

Request:

```json
{
  "customerRef": "demo-customer-1",
  "packageId": 1
}
```

`customerRef` is required and limited to 100 characters. `packageId` must be a positive
ID for an active package.

Success: `202 Accepted`, a `Location: /api/subscriptions/{id}` header, and a representation:

```json
{
  "id": 42,
  "customerRef": "demo-customer-1",
  "packageId": 1,
  "status": "PENDING",
  "createdAt": "2026-10-25T12:00:00Z",
  "updatedAt": "2026-10-25T12:00:00Z"
}
```

The same idempotency key and request payload return the original subscription. Reusing
the key with a different payload returns `409 Conflict`. A missing or inactive package
returns `404 Not Found`; invalid request data returns `400 Bad Request`.

## Get a subscription

`GET /api/subscriptions/{id}`

Returns the subscription representation above, or `404 Not Found` when the ID does not
exist.

## Error format

Errors use Spring's `ProblemDetail` response format with `type`, `title`, `status`, and
`detail` fields.