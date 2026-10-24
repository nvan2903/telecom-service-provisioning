# Telecom Service Provisioning

Spring Boot service for telecom service provisioning.

## Requirements

- JDK 25
- Docker Compose

## Run locally

Set the local PostgreSQL password in the current PowerShell session:

```powershell
$env:POSTGRES_PASSWORD = "choose-a-local-password"
$env:DB_PASSWORD = $env:POSTGRES_PASSWORD
```

Start PostgreSQL:

```powershell
docker compose up -d postgres
```

Run the application:

```powershell
.\mvnw.cmd spring-boot:run
```

Run tests:

```powershell
.\mvnw.cmd test
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
