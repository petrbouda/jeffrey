# Jeffrey - JFR Analyst Project

## Project Overview
Jeffrey is a JFR (Java Flight Recorder) analysis tool that specializes in visualizing JFR events using various types of graphs. The project helps developers profile Java applications and identify performance bottlenecks to optimize code for better speed and resource consumption.

## Architecture
This is a full-stack application with:
- **Backend**: Java 25 + Spring Boot 4.0.0 + Jersey/JAX-RS
- **Frontend**: Vue 3 SPA with TypeScript
- **Build System**: Maven (Java) + Vite (Frontend)
- **Database**: DuckDB 1.4.1.0 (platform DB + per-profile DBs)
- **AI Integration**: Spring AI 2.0.0-M2 (Claude/OpenAI providers)
- **CLI**: GraalVM Native Image

### Backend Domain Architecture

The backend is organized into three top-level directories: **platform/**, **profiles/**, and **shared/**

```
┌─────────────────────────────────────────────────────────────────┐
│                        PLATFORM                                  │
│  Workspaces → Projects → Recordings → Profiles List              │
│  + Sessions, Scheduling, Remote Workspaces                       │
│                                                                  │
│  Module: platform/platform-management                            │
│  Persistence: platform/platform-sql-persistence                  │
│  Providers: platform/providers/                                  │
└──────────────────────────────┬──────────────────────────────────┘
                               │ triggers profile creation/analysis
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                       PROFILES                                   │
│                                                                  │
│  ┌───────────────────────┐       ┌───────────────────────────┐  │
│  │   recording-parser    │       │   profile-management      │  │
│  │                       │       │                           │  │
│  │  Path/InputStream     │       │  ProfileManager           │  │
│  │        ↓              │──────>│  Analysis features        │  │
│  │  Parse JFR → Store DB │       │  (Flamegraph, Timeseries,  │  │
│  │        ↓              │       │   Guardian, GC, Threads,  │  │
│  │  Returns ProfileInfo  │       │   HeapDump, AI Analysis)  │  │
│  │                       │       │                           │  │
│  │  profiles/recording-parser    │  profiles/profile-management │
│  └───────────────────────┘       └───────────────────────────┘  │
│                                                                  │
│  AI: profiles/ai-assistant, profiles/duckdb-ai-mcp,             │
│      profiles/heap-dump-ai-mcp                                   │
│  Persistence: profiles/profile-sql-persistence                   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        SHARED                                    │
│  Common utilities, persistence abstractions, test infrastructure │
│  Module: shared/common, shared/persistence, shared/test          │
└─────────────────────────────────────────────────────────────────┘
```

**Platform** (`platform/`):
- `platform-management` — Workspaces, projects, recordings, sessions, scheduling, remote workspace support
- `platform-sql-persistence` — Platform-level DuckDB persistence (shared database)
- `platform-persistence-api` — Persistence interfaces for platform domain
- `providers/` — Recording storage (filesystem-recording-storage, recording-storage-api)
- `sql-builder` — SQL query building utilities
- `jfr-repository-parser` — JFR repository streaming parser
- `persistent-queue` — Durable event queue
- `tools` — JDK tooling utilities
- REST resources: `/api/internal/workspaces/**`, `/api/internal/projects/**`, `/api/public/**`

**Profiles** (`profiles/`):
- `profile-management` — All profile analysis features (Flamegraph, Timeseries, Guardian, GC, Threads, HeapDump, AI)
- `recording-parser/` — JFR parsing (jfr-parser-api, jdk-jfr-parser, db-jfr-parser)
- `profile-sql-persistence` — Per-profile DuckDB persistence (isolated database per profile)
- `profile-persistence-api` — Persistence interfaces for profile domain
- `common-profile` — Shared profile utilities
- `flamegraph`, `timeseries`, `subsecond`, `profile-guardian`, `profile-thread`, `frame-ir` — Analysis modules
- `heap-dump` — Heap dump analysis
- `ai-assistant` — AI-powered profile analysis
- `duckdb-ai-mcp`, `heap-dump-ai-mcp` — MCP servers for AI integration
- REST resources: `/api/internal/profiles/**`

**Shared** (`shared/`):
- `common` — Shared utilities and models
- `persistence` — Common persistence abstractions
- `test` — Test infrastructure (`@DuckDBTest` annotation, test utilities)
- `folder-queue` — File-based queue implementation
- `turso-datasource` — Turso database support

## Technology Stack

### Backend (Java)
- **Java**: Version 25
- **Spring Boot**: 4.0.0 with Jersey/JAX-RS for REST APIs
- **Maven**: Build tool and dependency management
- **DuckDB**: 1.4.1.0 — Two-tier database architecture (platform DB + per-profile DBs)
- **Flyway**: 10.24.0 for database migrations
- **Jackson**: 2.19.2 for JSON serialization
- **Spring AI**: 2.0.0-M2 for AI integration (Claude/OpenAI)
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
├── platform/                          # Platform domain (workspaces, projects, recordings)
│   ├── platform-management/           # Core platform logic + REST resources
│   ├── platform-persistence-api/      # Platform persistence interfaces
│   ├── platform-sql-persistence/      # Platform DuckDB persistence
│   ├── providers/                     # Recording storage providers
│   │   ├── recording-storage-api/     # Storage interfaces
│   │   └── filesystem-recording-storage/
│   ├── jfr-repository-parser/         # JFR repository streaming parser
│   ├── persistent-queue/              # Durable event queue
│   ├── sql-builder/                   # SQL query building
│   └── tools/                         # JDK tooling utilities
├── profiles/                          # Profile analysis domain
│   ├── profile-management/            # Profile analysis features + REST resources
│   ├── recording-parser/              # JFR parsing
│   │   ├── jfr-parser-api/            # Parser interfaces
│   │   ├── jdk-jfr-parser/            # JDK-based parser
│   │   └── db-jfr-parser/             # Database-based parser
│   ├── profile-persistence-api/       # Profile persistence interfaces
│   ├── profile-sql-persistence/       # Per-profile DuckDB persistence
│   ├── common-profile/                # Shared profile utilities
│   ├── flamegraph/                    # Flame graph generation
│   ├── timeseries/                    # Time series analysis
│   ├── subsecond/                     # Sub-second analysis
│   ├── profile-guardian/              # Profile validation/checks
│   ├── profile-thread/                # Thread analysis
│   ├── frame-ir/                      # Frame intermediate representation
│   ├── heap-dump/                     # Heap dump analysis
│   ├── ai-assistant/                  # AI-powered analysis
│   ├── duckdb-ai-mcp/                 # DuckDB MCP server for AI
│   └── heap-dump-ai-mcp/             # Heap dump MCP server for AI
├── shared/                            # Shared modules
│   ├── common/                        # Common utilities and models
│   ├── persistence/                   # Common persistence abstractions
│   ├── test/                          # Test infrastructure (@DuckDBTest)
│   ├── folder-queue/                  # File-based queue
│   └── turso-datasource/             # Turso database support
├── pages/                             # Vue.js frontend application
│   ├── src/
│   │   ├── assets/                    # Design tokens, SCSS, static assets
│   │   ├── components/                # Reusable Vue components
│   │   ├── composables/               # Vue 3 composables (useModal, useNavigation, etc.)
│   │   ├── services/                  # API clients and utilities
│   │   │   └── api/                   # BasePlatformClient, BaseProfileClient, feature clients
│   │   ├── stores/                    # Simple ref-based stores
│   │   ├── views/                     # Page components
│   │   └── router/                    # Vue Router configuration
│   └── package.json
├── jeffrey-cli/                       # CLI tool (GraalVM Native Image)
├── jeffrey-agent/                     # Agent module
├── jeffrey-pages/                     # Documentation site
├── build/                             # Build configurations
│   ├── build-app/                     # Application assembly
│   ├── build-cli/                     # CLI build
│   ├── build-cli-native/             # Native image build
│   └── build-agent/                   # Agent build
├── jmh-tests/                         # JMH benchmarks
├── docker/                            # Docker configurations
└── pom.xml                            # Root Maven configuration
```

## Code Style and Conventions

### Java Backend
- **Package Structure**: `pbouda.jeffrey.*` with feature-based organization
- **Naming**: PascalCase for classes, camelCase for methods/fields
- **Architecture**: Manager pattern with service layer separation
- **REST**: Jersey/JAX-RS with `@Path` annotations, constructor injection (not `@Autowired`)
- **Sealed Interfaces**: Used for type-safe hierarchies (e.g., `JobDescriptor`, `WorkspacesManager`, `TimeRange`)
- **Records**: Used for DTOs and immutable data
- **Two-Tier Persistence**: Platform DB (shared, all workspaces/projects) + Profile DB (isolated per profile)
- **Resource Hierarchy**: Internal (`/api/internal/`) for frontend, Public (`/api/public/`) for remote workspace communication
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
- **Design Tokens**: CSS custom properties in `pages/src/assets/design-tokens.css` — always use these for colors, spacing, typography
- **Composables**: Reusable reactive logic in `pages/src/composables/` (useModal, useNavigation, useAiAnalysis, useWorkspaceType, etc.)
- **API Clients**: Two base classes in `pages/src/services/api/`:
  - `BasePlatformClient` — for workspace/project APIs (used by WorkspaceClient, ProjectClient)
  - `BaseProfileClient` — for profile feature APIs (used by OqlAssistantClient, ProfileMethodTracingClient, etc.)
- **State Management**: Simple ref-based stores in `pages/src/stores/` (not Pinia)
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
- **Frontend Dev**: `cd pages && npm run dev`
- **Frontend Build**: `cd pages && npm run build`
- **Frontend Lint**: `cd pages && npm run lint`
- **Frontend Format**: `cd pages && npm run format`
- **Frontend Test**: `cd pages && npm run test`
- **Frontend Protobuf**: `cd pages && npm run proto:generate`

## API Structure
- Two API paths: `/api/internal/` (frontend-facing) and `/api/public/` (remote workspace communication)
- Implemented using Jersey/JAX-RS (not Spring MVC)
- Platform resources in `platform/platform-management/.../resources/`
- Profile resources in `profiles/profile-management/.../resources/`
- JSON data exchange format
- Multi-part file uploads for JFR files

## Development Workflow
1. Backend development in Java with Spring Boot
2. Frontend development with Vue 3 and TypeScript
3. Integration through REST APIs
4. Docker containerization for deployment
5. Maven for Java build management, npm for frontend dependencies

## Testing
- **Backend**: JUnit 5 tests, with nested JUnit classes to group logical parts
- **Backend**: Mockito for mocking dependencies
- **Backend**: `@DuckDBTest` custom annotation for database integration tests (from `shared/test`)
- **Backend**: Use `java.time.Clock` instead of real timestamps to fix time
- **Frontend**: Vitest (`cd pages && npm run test`)

## AI Integration
- Spring AI 2.0.0-M2 with Claude and OpenAI providers
- AI modules: `profiles/ai-assistant/`, `profiles/duckdb-ai-mcp/`, `profiles/heap-dump-ai-mcp/`
- Config: `jeffrey.ai.provider=claude`, `jeffrey.ai.model=claude-opus-4-6`

## DuckDB MCP Servers
- You can use MCP Server to connect to DuckDB database to get information about the current data

### Structure of the Database
- Two-tier architecture: platform database (shared) and per-profile databases (isolated)
- Platform DB: workspaces, projects, recordings, sessions, scheduling
- Profile DB: events, flamegraph data, analysis results for a single profile
- `profile_id` gathers all data related to a specific profile

### Database Schema
- Platform migrations: `platform/platform-sql-persistence/src/main/resources/db/migration/platform/V001__init.sql`
- Profile migrations: `profiles/profile-sql-persistence/src/main/resources/db/migration/profile/V001__init.sql`
- JFR Event Types reference: https://sap.github.io/jfrevents/ (select Java version for event details)
- JSONB `fields` column in the `events` table contains event-specific data — see `/jfr-event-fields` skill for full field reference per event type

## Documentation Sync (Jeffrey Pages)

When modifying code, keep the corresponding documentation pages in `jeffrey-pages/` up to date. The docs are organized by domain:

| Code module | Documentation pages |
|---|---|
| `platform/platform-management` | `jeffrey-pages/src/views/docs/platform/` — workspaces, projects, recordings, sessions, scheduler, profiler settings, alerts |
| `profiles/profile-management` | `jeffrey-pages/src/views/docs/profiles/` — visualization, application analysis, JVM internals, heap dump analysis |
| `jeffrey-cli/` | `jeffrey-pages/src/views/docs/cli/` — CLI overview, configuration, directory structure, generated output |
| Architecture changes | `jeffrey-pages/src/views/docs/architecture/` — overview, public API, storage |
| Deployment changes | `jeffrey-pages/src/views/docs/deployments/` — JAR, container, live recording |

## License
GNU Affero General Public License v3.0 (AGPL-3.0)
