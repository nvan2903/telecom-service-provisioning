# Local development

## Prerequisites

- JDK 25
- Docker Desktop with Linux containers

## Start PostgreSQL and Kafka

From the repository root, set a local database password in PowerShell; do not commit it:

```powershell
$env:POSTGRES_PASSWORD = Read-Host "Local PostgreSQL password"
$env:DB_PASSWORD = $env:POSTGRES_PASSWORD
docker compose up -d postgres kafka
```

For instructions on changing a password for an existing Compose volume, see the root
`README.md`.

## Run the application and tests

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
.\mvnw.cmd -Pintegration-tests verify
```

The integration profile starts disposable PostgreSQL and Kafka containers through
Testcontainers. Stop the Compose dependencies when finished:

```powershell
docker compose down
```
