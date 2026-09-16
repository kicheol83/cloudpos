# CloudPos

Multi-tenant restaurant POS SaaS. Phase 0 — walking skeleton.

## What this phase proves

One claim, end to end: **PostgreSQL Row-Level Security scopes every query to the calling
tenant, and no repository method can be written that bypasses it.**

`TenantIsolationTest` is the proof. It seeds two tenants, queries with no tenant filter
in the repository, and asserts each tenant sees only its own rows — and that an unbound
request sees nothing at all.

## First-time setup

Requires JDK 21 and Docker.

### Windows (PowerShell)

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install Docker.DockerDesktop
winget install Gradle.Gradle

# Generate the Gradle wrapper once, then Gradle itself is no longer needed.
gradle wrapper --gradle-version 8.14
```

### macOS / Linux

```bash
brew install --cask temurin@21
brew install gradle
gradle wrapper --gradle-version 8.14
```

`gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` are committed after this.

## Running it

### Windows (PowerShell)

```powershell
.\make.ps1 up.core
.\make.ps1 run.identity
.\make.ps1 run.gateway

$headers = @{ "X-Dev-Tenant-Id" = "00000000-0000-0000-0000-00000000000a" }
Invoke-RestMethod -Uri "http://localhost:8080/v1/stores" -Headers $headers
```

### macOS / Linux

```bash
make up.core
make run.identity
make run.gateway

curl -H 'X-Dev-Tenant-Id: 00000000-0000-0000-0000-00000000000a' \
     http://localhost:8080/v1/stores
```

Local ports are deliberately non-standard — PostgreSQL on 5435, Redis on 6380 — so the
stack never collides with other projects. Override with `POSTGRES_PORT`, `REDIS_PORT`,
`KAFKA_PORT` or `CLICKHOUSE_PORT`.

`bootRun` activates the `local` Spring profile automatically, so no environment
variables are needed.

See ADR 0018 for why you should never run the full stack.

## Layout

| Path | Contents |
| --- | --- |
| `contracts/` | OpenAPI documents and event schemas — the source of truth |
| `libs/` | `tenancy` (RLS binding), `web` (problem details) |
| `services/` | `gateway`, `identity` |
| `infra/compose/` | Profiled local environment |
| `docs/` | ADRs, architecture, domain model, contracts |

## Documentation

Start with `docs/adr/README.md`. For the reasoning behind this skeleton specifically:
ADR 0004 (tenancy), ADR 0005 (database per service), ADR 0018 (local environment),
ADR 0019 (monorepo and CI).

## Design credit

The UI design is the CloudPos Point of Sale UI Kit by AJP Design. Architecture,
implementation and all backend work are original.
