# Jeffrey - JFR Analyst Project

## Project Overview
Jeffrey is a JFR (Java Flight Recorder) analysis tool that specializes in visualizing JFR events using various types of graphs. The project helps developers profile Java applications and identify performance bottlenecks to optimize code for better speed and resource consumption.

## Architecture
This is a full-stack application with two deployment modes:
- **Backend**: Java 25 + Spring Boot 4.0.4 + Jersey/JAX-RS + gRPC 1.72.0
- **Frontend**: Vue 3 SPA with TypeScript (separate frontends for local and server deployments)
- **Build System**: Maven (Java) + Vite (Frontend)
- **Database**: DuckDB 1.5.0.0 (three-tier: local core DB + server DB + per-profile DBs)
- **AI Integration**: Spring AI 2.0.0-M3 (Claude/OpenAI providers)
- **Remote Communication**: gRPC (proto files in `shared/server-api/`)
- **CLI**: GraalVM Native Image

### Deployment Architecture

The project supports two deployment modes: **jeffrey-local** (standalone) and **jeffrey-server** (multi-workspace server). They share common modules via **shared/**.

```
┌─────────────────────────────────────────────────────────────────┐
│                     JEFFREY-LOCAL (standalone)                    │
│  LocalApplication — full-featured single-user deployment         │
│                                                                  │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │  core-local   │  │  pages-local      │  │  profiles/       │  │
│  │  REST + gRPC  │  │  Full Vue 3 SPA   │  │  Profile analysis │  │
│  │  clients      │  │                   │  │  modules          │  │
│  └──────────────┘  └──────────────────┘  └──────────────────┘  │
│                                                                  │
│  Persistence: local-core-sql-persistence (local core DB)         │
│  + profiles/profile-sql-persistence (per-profile DBs)            │
└──────────────────────────┬──────────────────────────────────────┘
                           │ gRPC communication
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                     JEFFREY-SERVER (remote)                       │
│  ServerApplication — multi-workspace server with scheduling      │
│                                                                  │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │  core-server  │  │  pages-server     │  │  shared/          │  │
│  │  gRPC services│  │  Minimal Vue 3    │  │  persistent-queue │  │
│  │  scheduler    │  │  UI               │  │                   │  │
│  └──────────────┘  └──────────────────┘  └──────────────────┘  │
│                                                                  │
│  Persistence: server-sql-persistence (server DB)                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        SHARED                                    │
│  Common utilities, persistence abstractions, test infrastructure │
│  gRPC proto definitions, storage, SQL builder                    │
│  Modules: common, persistence, test, server-api, sql-builder,   │
│           recording-storage-api, filesystem-recording-storage,   │
│           folder-queue, persistent-queue                         │
└─────────────────────────────────────────────────────────────────┘
```

**jeffrey-local** (`jeffrey-local/`):
- `core-local` — Main Spring Boot app (LocalApplication), REST resources, managers, gRPC clients for remote workspace communication
- `local-core-persistence-api` — Persistence interfaces for local core domain
- `local-core-sql-persistence` — DuckDB persistence for local core (workspaces, projects, recordings, remote workspace connections)
- `pages-local` — Full-featured Vue 3 SPA frontend
- `profiles/` — All profile analysis modules (see below)
- REST resources: `/api/internal/workspaces/**`, `/api/internal/projects/**`, `/api/internal/profiles/{profileId}/**`

**jeffrey-local/profiles/** (profile analysis, used only by jeffrey-local):
- `profile-management` — Profile analysis features + REST resources (Flamegraph, Timeseries, Guardian, GC, Threads, HeapDump, AI)
- `recording-parser/` — JFR parsing (jfr-parser-api, jdk-jfr-parser, db-jfr-parser)
- `profile-sql-persistence` — Per-profile DuckDB persistence (isolated database per profile)
- `profile-persistence-api` — Persistence interfaces for profile domain
- `common-profile` — Shared profile utilities
- `flamegraph`, `timeseries`, `subsecond`, `profile-guardian`, `profile-thread`, `frame-ir` — Analysis modules
- `heap-dump` — Heap dump analysis
- `ai-config` — AI configuration for profile analysis
- `oql-assistant` — OQL AI assistant
- `duckdb-ai-mcp`, `heap-dump-ai-mcp` — MCP servers for AI integration

**jeffrey-server** (`jeffrey-server/`):
- `core-server` — Main Spring Boot app (ServerApplication), gRPC service implementations, scheduler/jobs, JFR streaming
- `server-persistence-api` — Persistence interfaces for server domain
- `server-sql-persistence` — DuckDB persistence for server (workspaces, projects, scheduling)
- `pages-server` — Minimal Vue 3 frontend
- `shared/persistent-queue` — Server-specific persistent queue

**shared** (`shared/`):
- `common` — Shared utilities and models
- `persistence` — Common persistence abstractions
- `test` — Test infrastructure (`@DuckDBTest` annotation, test utilities)
- `server-api` — gRPC proto files at `src/main/proto/jeffrey/api/v1/`
- `sql-builder` — SQL query building utilities
- `recording-storage-api` — Storage interfaces
- `filesystem-recording-storage` — Filesystem storage implementation
- `folder-queue` — File-based queue implementation
- `persistent-queue` — Durable event queue

## Technology Stack

### Backend (Java)
- **Java**: Version 25
- **Spring Boot**: 4.0.4 with Jersey/JAX-RS for REST APIs
- **Maven**: Build tool and dependency management
- **DuckDB**: 1.5.0.0 — Three-tier database architecture (local core DB + server DB + per-profile DBs)
- **Flyway**: 10.24.0 for database migrations
- **Jackson**: 2.21.1 for JSON serialization
- **gRPC**: 1.72.0 for remote workspace communication (replaces old REST public API)
- **Protobuf**: 4.30.2 for gRPC message serialization
- **Spring AI**: 2.0.0-M3 for AI integration (Claude/OpenAI)
- **Logging**: SLF4J with Logback

### Frontend (Vue 3)
- **Vue 3**: 3.5.13 — Composition API
- **TypeScript**: 5.5.2
- **Vite**: 6.0.5 — Build tool and dev server
- **Vitest**: 4.1.0 — Unit testing
- **Vue Router**: 4.3.3 — Client-side routing
- **ApexCharts**: 5.10.0 — Data visualization
- **Bootstrap 5**: 5.3.3 — CSS framework with custom styling
- **Axios**: 1.8.3 — HTTP client
- **Konva**: 9.3.20 — Canvas rendering
- **Protobuf**: 7.4.0 — Binary data (flamegraph)
- **marked**: 17.0.1 — Markdown rendering
- **mitt**: 3.0.1 — Event bus

## Project Structure

```
jeffrey/
├── jeffrey-local/                     # Standalone local deployment
│   ├── core-local/                    # Main Spring Boot app (LocalApplication)
│   │   └── src/.../local/core/
│   │       ├── client/                # gRPC clients (RemoteClients, Remote*Client)
│   │       ├── manager/               # Managers (project/, workspace/, downloads, recordings, etc.)
│   │       └── resources/             # REST resources (project/, workspace/, ProfilesResource, etc.)
│   ├── local-core-persistence-api/    # Local core persistence interfaces
│   ├── local-core-sql-persistence/    # Local core DuckDB persistence
│   ├── pages-local/                   # Full-featured Vue 3 SPA frontend
│   │   └── src/
│   │       ├── assets/                # Design tokens, SCSS, static assets
│   │       ├── components/            # Reusable Vue components
│   │       ├── composables/           # Vue 3 composables (useModal, useNavigation, etc.)
│   │       ├── services/              # API clients and utilities
│   │       │   └── api/               # BasePlatformClient, BaseProfileClient, feature clients
│   │       ├── stores/                # Simple ref-based stores
│   │       ├── styles/                # Shared CSS files
│   │       ├── views/                 # Page components
│   │       └── router/                # Vue Router configuration
│   └── profiles/                      # Profile analysis modules
│       ├── profile-management/        # Profile analysis features + REST resources
│       │   └── src/.../profile/
│       │       ├── manager/           # Profile managers
│       │       └── resources/         # Profile REST resources (Flamegraph, Timeseries, etc.)
│       ├── recording-parser/          # JFR parsing
│       │   ├── jfr-parser-api/        # Parser interfaces
│       │   ├── jdk-jfr-parser/        # JDK-based parser
│       │   └── db-jfr-parser/         # Database-based parser
│       ├── profile-persistence-api/   # Profile persistence interfaces
│       ├── profile-sql-persistence/   # Per-profile DuckDB persistence
│       ├── common-profile/            # Shared profile utilities
│       ├── flamegraph/                # Flame graph generation
│       ├── timeseries/                # Time series analysis
│       ├── subsecond/                 # Sub-second analysis
│       ├── profile-guardian/          # Profile validation/checks
│       ├── profile-thread/            # Thread analysis
│       ├── frame-ir/                  # Frame intermediate representation
│       ├── heap-dump/                 # Heap dump analysis
│       ├── ai-config/                 # AI configuration
│       ├── oql-assistant/             # OQL AI assistant
│       ├── duckdb-ai-mcp/            # DuckDB MCP server for AI
│       └── heap-dump-ai-mcp/         # Heap dump MCP server for AI
├── jeffrey-server/                    # Multi-workspace server deployment
│   ├── core-server/                   # Main Spring Boot app (ServerApplication)
│   │   └── src/.../server/core/
│   │       ├── grpc/                  # gRPC service implementations
│   │       ├── scheduler/             # Job scheduler, job definitions
│   │       │   └── job/               # Job implementations + descriptor/
│   │       ├── resources/             # REST resources (WorkspacesResource, GrpcDocsResource)
│   │       └── streaming/             # JFR streaming
│   ├── server-persistence-api/        # Server persistence interfaces
│   ├── server-sql-persistence/        # Server DuckDB persistence
│   ├── pages-server/                  # Minimal Vue 3 frontend
│   └── shared/                        # Server-specific shared modules
│       └── persistent-queue/          # Server persistent queue
├── shared/                            # Shared modules (used by both deployments)
│   ├── common/                        # Common utilities and models
│   ├── persistence/                   # Common persistence abstractions
│   ├── test/                          # Test infrastructure (@DuckDBTest)
│   ├── server-api/                    # gRPC proto definitions
│   │   └── src/main/proto/jeffrey/api/v1/  # Proto files
│   ├── sql-builder/                   # SQL query building
│   ├── recording-storage-api/         # Storage interfaces
│   ├── filesystem-recording-storage/  # Filesystem storage implementation
│   ├── folder-queue/                  # File-based queue
│   └── persistent-queue/             # Durable event queue
├── jeffrey-cli/                       # CLI tool (GraalVM Native Image)
├── jeffrey-agent/                     # Agent module
├── jeffrey-pages/                     # Documentation site
├── build/                             # Build configurations
│   ├── build-local/                   # Local application assembly
│   ├── build-server/                  # Server application assembly
│   ├── build-cli/                     # CLI build
│   ├── build-cli-native/             # Native image build
│   ├── build-agent/                   # Agent build
│   └── scripts/                       # Build scripts
├── jmh-tests/                         # JMH benchmarks
├── manual-tests/                      # Manual testing
├── docker/                            # Docker configurations
└── pom.xml                            # Root Maven configuration
```

## Code Style and Conventions

### Java Backend
- **Package Structure**: `pbouda.jeffrey.local.*` for local deployment, `pbouda.jeffrey.server.*` for server deployment, `pbouda.jeffrey.profile.*` for profiles, `pbouda.jeffrey.*` for shared modules
- **Naming**: PascalCase for classes, camelCase for methods/fields
- **Architecture**: Manager pattern with service layer separation
- **REST**: Jersey/JAX-RS with `@Path` annotations, constructor injection (not `@Autowired`)
- **gRPC**: Proto files in `shared/server-api/`, implementations in `jeffrey-server/core-server/.../grpc/`, clients in `jeffrey-local/core-local/.../client/`
- **Sealed Interfaces**: Used for type-safe hierarchies (e.g., `JobDescriptor`, `WorkspacesManager`, `TimeRange`)
- **Records**: Used for DTOs and immutable data
- **Three-Tier Persistence**: Local Core DB (workspaces, projects, recordings) + Server DB (server workspaces, projects, scheduling) + Profile DB (isolated per profile)
- **Resource Hierarchy**: Internal (`/api/internal/`) for frontend, gRPC for remote workspace communication
- **Copyright Headers**: All Java files must include the AGPL license header with the current year (2026):
  ```java
  /*
   * Jeffrey
   * Copyright (C) 2026 Petr Bouda
   *
   * This program is free software: you can redistribute it and/or modify
   * it under the terms of the GNU Affero General Public License as published by
   * the Free Software Foundation, either version 3 of the License, or
   * (at your option) any later version.
   *
   * This program is distributed in the hope that it will be useful,
   * but WITHOUT ANY WARRANTY; without even the implied warranty of
   * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   * GNU Affero General Public License for more details.
   *
   * You should have received a copy of the GNU Affero General Public License
   * along with this program.  If not, see <http://www.gnu.org/licenses/>.
   */
  ```
- **Error Handling**: Custom exceptions with proper HTTP status mapping
- **Logging**: Use SLF4J with structured key-value format:
  - Pattern: `"Description of what happened: key1={} key2={} key3={}"`
  - No commas between key-value pairs
  - Example: `LOG.warn("Chunk extends beyond file, truncating: chunkIndex={} position={} claimedSize={}", index, pos, size)`
- **Time Handling**: Always use `java.time.Clock` instead of `Instant.now()` or `System.currentTimeMillis()`:
  - Inject `Clock` as a constructor parameter for testability
  - Use `clock.instant()` to get the current time
  - Example: `private final Clock clock; ... Instant now = clock.instant();`
  - This allows tests to use fixed time via `Clock.fixed()` for deterministic behavior

### Frontend (Vue/TypeScript)
- **Components**: PascalCase for component names
- **Composition API**: Preferred over Options API
- **TypeScript**: Strict typing with interfaces for API models
- **Design Tokens**: CSS custom properties in `jeffrey-local/pages-local/src/assets/design-tokens.css` — always use these for colors, spacing, typography
- **Composables**: Reusable reactive logic in `jeffrey-local/pages-local/src/composables/` (useModal, useNavigation, useAiAnalysis, useWorkspaceType, etc.)
- **API Clients**: Two base classes in `jeffrey-local/pages-local/src/services/api/`:
  - `BasePlatformClient` — for workspace/project APIs (used by WorkspaceClient, ProjectClient)
  - `BaseProfileClient` — for profile feature APIs (used by OqlAssistantClient, ProfileMethodTracingClient, etc.)
- **State Management**: Simple ref-based stores in `jeffrey-local/pages-local/src/stores/` (not Pinia)
- **Protobuf**: Used for flamegraph binary data; regenerate with `npm run proto:generate`
- **Styling**: Use shared CSS files first, then scoped CSS for component-specific styles
  - **Shared CSS files** (import via `import '@/styles/...'` or `@import` in SCSS):
    - `@/styles/shared-components.css` - Common UI patterns (search-container, cards, buttons, loading/empty states)
    - `@/assets/_sidebar-menu.scss` - Sidebar navigation styles (nav-item, nav-submenu, disabled-feature)
  - Always check shared CSS files before adding new scoped styles
  - Add commonly reused styles to shared files to avoid duplication
- **File Organization**: Feature-based grouping with shared components
- Formatting values use FormattingService, which provides consistent formatting across the application, propose a new function if you miss something
- Always try to use Vue Components first. If you need create a new to deduplicate code, suggest it and create it.

### Build Commands
- **Java Version**: `sdk use java 25.0.1-amzn`
- **Backend Compile**:
  ```bash
  JAVA_HOME=/Users/petrbouda/.sdkman/candidates/java/25.0.1-amzn /Users/petrbouda/.sdkman/candidates/maven/current/bin/mvn clean compile
  ```
- **Frontend Dev**: `cd jeffrey-local/pages-local && npm run dev`
- **Frontend Build**: `cd jeffrey-local/pages-local && npm run build`
- **Frontend Lint**: `cd jeffrey-local/pages-local && npm run lint`
- **Frontend Format**: `cd jeffrey-local/pages-local && npm run format`
- **Frontend Test**: `cd jeffrey-local/pages-local && npm run test`
- **Frontend Protobuf**: `cd jeffrey-local/pages-local && npm run proto:generate`

## API Structure
- **jeffrey-local REST**: `/api/internal/` for frontend-facing APIs — resources in `jeffrey-local/core-local/.../resources/`
- **Profile REST**: `/api/internal/profiles/{profileId}/` for profile features — resources in `jeffrey-local/profiles/profile-management/.../resources/`
- **jeffrey-server REST**: `/api/internal/` for minimal server UI — resources in `jeffrey-server/core-server/.../resources/`
- **gRPC**: Remote workspace communication between jeffrey-local and jeffrey-server — proto definitions in `shared/server-api/src/main/proto/jeffrey/api/v1/`, service implementations in `jeffrey-server/core-server/.../grpc/`, clients in `jeffrey-local/core-local/.../client/`
- gRPC proto files: `workspace_service.proto`, `project_service.proto`, `instance_service.proto`, `recording_download_service.proto`, `repository_service.proto`, `profiler_settings_service.proto`, `messages_service.proto`
- gRPC clients: `RemoteClients` record containing `RemoteDiscoveryClient`, `RemoteRepositoryClient`, `RemoteRecordingStreamClient`, `RemoteProfilerClient`, `RemoteMessagesClient`, `RemoteInstancesClient`, `RemoteProjectsClient`
- Implemented using Jersey/JAX-RS (not Spring MVC) for REST
- JSON data exchange format for REST, Protobuf for gRPC
- Multi-part file uploads for JFR files

## Development Workflow
1. Backend development in Java with Spring Boot (two deployment targets: local and server)
2. Frontend development with Vue 3 and TypeScript (primary UI in `jeffrey-local/pages-local/`)
3. Integration through REST APIs (local) and gRPC (server communication)
4. Docker containerization for deployment
5. Maven for Java build management, npm for frontend dependencies

## Testing
- **Backend**: JUnit 5 tests, with nested JUnit classes to group logical parts
- **Backend**: Mockito for mocking dependencies
- **Backend**: `@DuckDBTest` custom annotation for database integration tests (from `shared/test`)
- **Backend**: Use `java.time.Clock` instead of real timestamps to fix time
- **Frontend**: Vitest (`cd jeffrey-local/pages-local && npm run test`)

## AI Integration
- Spring AI 2.0.0-M3 with Claude and OpenAI providers
- AI modules: `jeffrey-local/profiles/ai-config/`, `jeffrey-local/profiles/oql-assistant/`, `jeffrey-local/profiles/duckdb-ai-mcp/`, `jeffrey-local/profiles/heap-dump-ai-mcp/`
- Config: `jeffrey.ai.provider=claude`, `jeffrey.ai.model=claude-opus-4-6`

## DuckDB MCP Servers
- You can use MCP Server to connect to DuckDB database to get information about the current data

### Structure of the Database
- Three-tier architecture: local core database, server database, and per-profile databases (isolated)
- Local Core DB: workspaces, projects, recordings, remote workspace connections
- Server DB: server-side workspaces, projects, scheduling
- Profile DB: events, flamegraph data, analysis results for a single profile
- `profile_id` gathers all data related to a specific profile

### Database Schema
- Local Core migrations: `jeffrey-local/local-core-sql-persistence/src/main/resources/db/migration/local/core/V001__init.sql`
- Server migrations: `jeffrey-server/server-sql-persistence/src/main/resources/db/migration/server/V001__init.sql`
- Profile migrations: `jeffrey-local/profiles/profile-sql-persistence/src/main/resources/db/migration/profile/V001__init.sql`
- JFR Event Types reference: https://sap.github.io/jfrevents/ (select Java version for event details)
- JSONB `fields` column in the `events` table contains event-specific data — see `/jfr-event-fields` skill for full field reference per event type

## Documentation Sync (Jeffrey Pages)

When modifying code, keep the corresponding documentation pages in `jeffrey-pages/` up to date. The docs are organized by domain:

| Code module | Documentation pages |
|---|---|
| `jeffrey-local/core-local` | `jeffrey-pages/src/views/docs/platform/` — workspaces, projects, recordings, sessions, profiler settings, alerts |
| `jeffrey-server/core-server` | `jeffrey-pages/src/views/docs/platform/` — scheduler |
| `jeffrey-local/profiles/profile-management` | `jeffrey-pages/src/views/docs/profiles/` — visualization, application analysis, JVM internals, heap dump analysis |
| `jeffrey-cli/` | `jeffrey-pages/src/views/docs/cli/` — CLI overview, configuration, directory structure, generated output |
| Architecture changes | `jeffrey-pages/src/views/docs/architecture/` — overview, public API, storage |
| Deployment changes | `jeffrey-pages/src/views/docs/deployments/` — JAR, container, live recording |

## License
GNU Affero General Public License v3.0 (AGPL-3.0)
