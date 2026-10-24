# Project structure assessment

## Current state

This repository is a small Spring Boot/Maven starter, not yet a product implementation:

- The Maven source and test roots already follow the standard layout.
- The Java source currently contains only the application entry point.
- Resources contain Spring configuration and one Flyway migration for a health-check table.
- `compose.yaml` provides a local PostgreSQL dependency.
- There are no business modules, deployment manifests, CI workflows, or operational scripts yet.
- The requested documentation files exist but have not been populated.

There is no reason to split this codebase into multiple deployable services or Maven modules
yet. Creating empty package trees for every future layer would add ceremony without giving
the project real boundaries.

## Recommended approach

Start as a **modular monolith**, organized by business feature. Keep the Spring Boot
application class in the root package so component scanning covers the application. Add
feature packages only when the first real feature classes are introduced:

```text
src/
├── main/
│   ├── java/com/nvan/telecomprovisioning/
│   │   ├── TelecomServiceProvisioningApplication.java
│   │   ├── config/                    # cross-cutting Spring configuration
│   │   └── provisioning/              # provisioning feature boundary
│   │       ├── api/                   # HTTP controllers and request/response models
│   │       ├── application/           # use cases and application ports
│   │       ├── domain/                # business rules and domain model
│   │       └── infrastructure/       # persistence, Kafka, and external-system adapters
│   └── resources/
│       ├── application.yaml
│       └── db/migration/              # append-only Flyway migrations
└── test/
    ├── java/com/nvan/telecomprovisioning/
    │   ├── provisioning/              # tests alongside feature boundaries
    │   └── architecture/              # boundary tests, when introduced
    └── resources/                     # test-only configuration and fixtures
```

The tree above is a convention for code as it appears, not a requirement to create empty
directories now. If the service grows to include distinct capabilities (for example,
catalog, order, or provisioning), add those as sibling feature packages and give each the
layers it actually needs.

## Repository-level organization

```text
.
├── .editorconfig
├── README.md
├── compose.yaml                       # local development dependencies
├── docs/                              # product and engineering documentation
├── pom.xml
└── src/
```

Add `infra/` for real infrastructure-as-code, `.github/workflows/` for CI/CD definitions,
and `scripts/` for repeatable repository tasks when those assets are introduced. Keep
environment-specific deployment configuration out of application source. Do not add
placeholder directories or duplicate configuration until there is a concrete artifact to
put in them.

## Dependency and change rules

- Keep business logic in the feature's domain/application packages; keep Spring, SQL,
  Kafka, and vendor-specific code at the edges.
- Let API and persistence adapters call application use cases; do not make domain code
  depend on web, database, or messaging frameworks.
- Share code only when it is genuinely cross-feature; avoid a generic `common` dumping
  ground.
- Add schema changes as new Flyway migrations; do not rewrite migrations that may already
  have been applied.
- Keep tests in the same feature boundary as the production behavior they verify.
- Split into separately deployed services or Maven modules only when ownership, deployment,
  or scaling needs justify the added operational and dependency complexity.

## Known follow-up before production

The current datasource credentials are hard-coded for local development, SQL logging is
enabled, and no production deployment or CI/CD structure exists. These are separate
configuration and operations tasks; the local starter layout should not be treated as
production-ready.