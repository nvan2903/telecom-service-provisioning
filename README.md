# Telecom Service Provisioning

Spring Boot service for telecom service provisioning.

## Requirements

- JDK 25
- Docker Compose

## Run locally

Set a local PostgreSQL password in the current PowerShell session. Enter the same value
for both variables; do not commit or share it:

```powershell
$env:POSTGRES_PASSWORD = Read-Host "Local PostgreSQL password"
$env:DB_PASSWORD = $env:POSTGRES_PASSWORD
```

Compose uses `POSTGRES_PASSWORD` when it initializes a new database volume. PostgreSQL
stores credentials in the persistent volume, so changing this environment variable later
does not rotate the password of an existing database. Keep using the password already set
for that volume, or change it interactively without putting it in shell history:

```powershell
docker exec -it telecom-postgres psql -U telecom_user -d telecom_db
```

At the `psql` prompt run `\password telecom_user`, enter the new password twice, then run
`\q`. Set `POSTGRES_PASSWORD` and `DB_PASSWORD` to that same value in PowerShell before
starting Compose and the application. This updates the database role without deleting
the volume or its data.

Start PostgreSQL and Kafka (single-broker KRaft):

```powershell
docker compose up -d postgres kafka
```

The application running on the host connects to Kafka at `localhost:9092`. For an
application container in the same Compose network, set `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`.
The single broker is for local development only; it does not provide high availability.

Run the application:

```powershell
.\mvnw.cmd spring-boot:run
```

The application sets the JVM default timezone to UTC before creating database connections,
and Hibernate also uses UTC for JDBC timestamp handling. This avoids relying on the host
machine's timezone aliases, some of which PostgreSQL does not recognize.

Run tests:

```powershell
.\mvnw.cmd test
```

Run PostgreSQL/Kafka container integration tests (requires Docker):

```powershell
.\mvnw.cmd -Pintegration-tests verify
```

Stop the local database:

```powershell
docker compose down
```

The database password is supplied through environment variables and is not stored in the
repository. Use environment-specific configuration and managed secrets before deploying to
a shared or production environment.

## Project layout

- `src/main/java` — application code, organized by feature as features are implemented.
- `src/main/resources/db/migration` — versioned Flyway database migrations.
- `src/test/java` — automated tests, following the production package structure.
- `src/test/resources` — test-only configuration and fixtures.
- `docs` — requirements, design, API, operations, and implementation documentation.
- `compose.yaml` — local development dependencies.

See [docs/architecture.md](docs/architecture.md) for the current assessment and package
organization rules.
