# Observability and security

## Current implementation

- Spring Boot Actuator is included.
- The application uses Spring Security defaults; local development generates a `user`
  password at startup, and state-changing requests require CSRF protection.
- Database passwords are supplied through `DB_PASSWORD`; Compose requires
  `POSTGRES_PASSWORD`. Do not commit either value.
- Kafka and PostgreSQL in the local Compose file use development settings and are not
  configured for production authentication or transport encryption.
- Consumer failures are logged and exhausted records are sent to a DLT with Kafka
  diagnostic headers.

## Before shared or production deployment

- Define explicit authentication, authorization, CSRF, and secret-management policies.
- Enable TLS and appropriate authentication for PostgreSQL and Kafka; provision topics
  explicitly with reviewed retention, replication, and access controls.
- Define structured logging, correlation identifiers, metrics, alerting, and log-retention
  policies. Avoid logging credentials, tokens, or real customer data.
- Restrict Actuator endpoints to the minimum required and protect operational interfaces.

The local test setup is for synthetic data and does not represent a production security
assessment.
