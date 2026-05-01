# Jeffrey - JFR Analyst Project

## Project Overview
Jeffrey is a JFR (Java Flight Recorder) analysis tool that specializes in visualizing JFR events using various types of graphs. The project helps developers profile Java applications and identify performance bottlenecks to optimize code for better speed and resource consumption.

## Architecture
This is a full-stack application with two deployment modes:
- **Backend**: Java 25 + Spring Boot 4.0.4 + Jersey/JAX-RS + gRPC 1.72.0
- **Frontend**: Vue 3 SPA with TypeScript (separate frontends for microscope and server deployments)
- **Build System**: Maven (Java) + Vite (Frontend)
- **Database**: DuckDB 1.5.0.0 (three-tier: microscope core DB + server DB + per-profile DBs)
- **AI Integration**: Spring AI 2.0.0-M3 (Claude/OpenAI providers)
- **Remote Communication**: gRPC (proto files in `shared/server-api/`)
- **CLI**: GraalVM Native Image

### Deployment Architecture

The project supports two deployment modes: **jeffrey-microscope** (standalone) and **jeffrey-server** (multi-workspace server). They share common modules via **shared/**.

```
┌─────────────────────────────────────────────────────────────────┐
│                     JEFFREY-MICROSCOPE (standalone)                    │
│  MicroscopeApplication — full-featured single-user deployment         │
│                                                                  │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │  core-microscope   │  │  pages-microscope      │  │  profiles/       │  │
│  │  REST + gRPC  │  │  Full Vue 3 SPA   │  │  Profile analysis │  │
│  │  clients      │  │                   │  │  modules          │  │
│  └──────────────┘  └──────────────────┘  └──────────────────┘  │
│                                                                  │
│  Persistence: microscope-core-sql-persistence (microscope core DB)         │
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

**jeffrey-microscope** (`jeffrey-microscope/`):
- `core-microscope` — Main Spring Boot app (MicroscopeApplication), REST resources, managers, gRPC clients for remote workspace communication
- `microscope-core-persistence-api` — Persistence interfaces for microscope core domain
- `microscope-core-sql-persistence` — DuckDB persistence for microscope core (workspaces, projects, recordings, remote workspace connections)
- `pages-microscope` — Full-featured Vue 3 SPA frontend
- `profiles/` — All profile analysis modules (see below)
- REST resources: `/api/internal/workspaces/**`, `/api/internal/projects/**`, `/api/internal/profiles/{profileId}/**`

**jeffrey-microscope/profiles/** (profile analysis, used only by jeffrey-microscope):
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
- **DuckDB**: 1.5.0.0 — Three-tier database architecture (microscope core DB + server DB + per-profile DBs)
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
├── jeffrey-microscope/                     # Standalone deployment
│   ├── core-microscope/                    # Main Spring Boot app (MicroscopeApplication)
│   │   └── src/.../microscope/core/
│   │       ├── client/                # gRPC clients (RemoteClients, Remote*Client)
│   │       ├── manager/               # Managers (project/, workspace/, downloads, recordings, etc.)
│   │       └── resources/             # REST resources (project/, workspace/, ProfilesResource, etc.)
│   ├── microscope-core-persistence-api/    # Microscope core persistence interfaces
│   ├── microscope-core-sql-persistence/    # Microscope core DuckDB persistence
│   ├── pages-microscope/                   # Full-featured Vue 3 SPA frontend
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
│   ├── build-microscope/                   # Local application assembly
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
- **Package Structure**: `cafe.jeffrey.microscope.*` for microscope deployment, `cafe.jeffrey.server.*` for server deployment, `cafe.jeffrey.profile.*` for profiles, `cafe.jeffrey.*` for shared modules
- **Naming**: PascalCase for classes, camelCase for methods/fields
- **Imports**: Always use import statements; never use fully qualified class names inline in code
- **Annotation Placement**: Annotations on **classes**, **fields**, and **methods** always go on their own line directly above the declaration — never inline on the same line. Applies to `@Bean`, `@Configuration`, `@RequestMapping`, `@ResponseBody`, `@GetMapping` / `@PostMapping` / etc., `@Mock`, `@Test`, `@ExtendWith`, custom annotations, and so on. Annotations on **method/constructor parameters** (e.g. `@PathVariable`, `@RequestParam`, `@RequestBody`) stay inline next to the parameter — that's the standard form and keeps signatures readable.

  ```java
  // good
  @Mock
  WorkspacesManager workspacesManager;

  @Bean
  public WorkspacesController workspacesController(WorkspacesManager workspacesManager) {
      return new WorkspacesController(workspacesManager);
  }

  @GetMapping("/{workspaceId}")
  public WorkspaceResponse info(@PathVariable("workspaceId") String workspaceId) { ... }

  // bad
  @Mock WorkspacesManager workspacesManager;
  @Bean public WorkspacesController workspacesController(...) { ... }
  ```
- **Architecture**: Manager pattern with service layer separation
- **REST**: Spring MVC controllers annotated with `@RestController` + `@RequestMapping` at class level (this is the **only** stereotype the project allows — see Spring Bean Registration). Constructor injection only — never `@Autowired`. Controllers are picked up by Spring Boot's component scan rooted at the application's package; do not declare them as `@Bean` methods.
- **Spring Bean Registration**: Never use stereotype annotations (`@Component`, `@Service`, `@Repository`, `@Controller`) or `@Autowired`. **Exception:** `@RestController` is allowed (and required) on Spring MVC controllers — this is the only stereotype on the allow-list, because the controller layer is the single place where component scanning is more pragmatic than explicit wiring. Everything else (managers, services, factories, resolvers, web infrastructure) must be registered explicitly via `@Bean` methods in `@Configuration` classes or Spring 4 `BeanRegistrar`. This keeps wiring visible and explicit while letting the dispatcher discover handlers normally.
- **gRPC**: Proto files in `shared/server-api/` (package `cafe.jeffrey.server.api.v1`), implementations in `jeffrey-server/core-server/.../grpc/`, clients in `jeffrey-microscope/core-microscope/.../client/`
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
  - Example: `LOG.warn("Chunk extends beyond file, truncating: chunk_index={} position={} claimed_size={}", index, pos, size)`
- **Time Handling**: Always use `java.time.Clock` instead of `Instant.now()` or `System.currentTimeMillis()`:
  - Inject `Clock` as a constructor parameter for testability
  - Use `clock.instant()` to get the current time
  - Example: `private final Clock clock; ... Instant now = clock.instant();`
  - This allows tests to use fixed time via `Clock.fixed()` for deterministic behavior
- **Elapsed Time Measuring**: Use `cafe.jeffrey.shared.common.measure.Measuring` utility instead of manual `System.nanoTime()` bookkeeping:
  - `Measuring.r(runnable)` — runs a `Runnable`, returns `Duration`
  - `Measuring.s(supplier)` — runs a `Supplier<T>`, returns `Elapsed<T>` (duration + result)
  - Example: `Duration elapsed = Measuring.r(() -> doWork()); LOG.debug("Work completed: duration_in_sec={}", elapsed.toSeconds());`

### Java Best Practices
- **Prefer records for parameter grouping**: When a method has 3+ related parameters (e.g., sessionId + eventTypes + timeRange), group them into a record. This makes call sites readable and refactoring safe. Example: `LiveSubscriptionRequest(sessionIds, eventTypes)` instead of passing them individually.
- **Prefer records for callback grouping**: When multiple callbacks travel together (e.g., onBatch + onComplete + onError), group them into a record. Example: `StreamingCallbacks(onBatch, onComplete, onError)`.
- **Keep domain logic free of framework types**: Records, subscription objects, and domain classes should not depend on gRPC, Jersey, or Spring types. Map framework-specific types (e.g., `StatusRuntimeException`) at the boundary (controller/gRPC service), not in domain code. Example: `StreamingWindow` throws `IllegalArgumentException`, the gRPC service maps it to `INVALID_ARGUMENT`.
- **Use utility classes for repetitive framework boilerplate**: Extract common framework patterns into static utility classes. Example: `GrpcExceptions.notFound(description)` instead of `Status.NOT_FOUND.withDescription(description).asRuntimeException()`.
- **Validate in constructors**: Records with invariants should validate in compact constructors and throw standard Java exceptions (e.g., `IllegalArgumentException`), not framework-specific ones.
- **Compose, don't inherit**: Prefer composition with records and delegation over deep class hierarchies. Example: `ReplayStreamReader` (composite) delegates to `SingleRecordingFileReader` (per-file).
- **Temp directory lifecycle**: When a process creates temp files, create a dedicated subdirectory (with UUID for uniqueness) and delete the entire directory on close, rather than tracking individual files.

### Frontend (Vue/TypeScript)
- **Components**: PascalCase for component names
- **Composition API**: Preferred over Options API
- **TypeScript**: Strict typing with interfaces for API models
- **Design Tokens**: CSS custom properties in `jeffrey-microscope/pages-microscope/src/assets/design-tokens.css` — always use these for colors, spacing, typography
- **Composables**: Reusable reactive logic in `jeffrey-microscope/pages-microscope/src/composables/` (useModal, useNavigation, useAiAnalysis, useWorkspaceType, etc.)
- **API Clients**: Two base classes in `jeffrey-microscope/pages-microscope/src/services/api/`:
  - `BasePlatformClient` — for workspace/project APIs (used by WorkspaceClient, ProjectClient)
  - `BaseProfileClient` — for profile feature APIs (used by OqlAssistantClient, ProfileMethodTracingClient, etc.)
- **State Management**: Simple ref-based stores in `jeffrey-microscope/pages-microscope/src/stores/` (not Pinia)
- **Protobuf**: Used for flamegraph binary data; regenerate with `npm run proto:generate`
- **Styling**: Use shared CSS files first, then scoped CSS for component-specific styles
  - **Shared CSS files** (import via `import '@/styles/...'` or `@import` in SCSS):
    - `@/styles/shared-components.css` - Common UI patterns (search-container, cards, buttons, loading/empty states)
    - `@/assets/_sidebar-menu.scss` - Sidebar navigation styles (nav-item, nav-submenu, disabled-feature)
  - Always check shared CSS files before adding new scoped styles
  - Add commonly reused styles to shared files to avoid duplication
- **File Organization**: Feature-based grouping with shared components
- **Timestamps**: All timestamps are UTC epoch millis (numbers). Never use `new Date()` for parsing or formatting — always use `FormattingService` methods. Never propagate date strings from the backend; always use numeric UTC timestamps. Frontend form inputs like `datetime-local` must be converted to/from epoch millis at the boundary.
- Formatting values use FormattingService, which provides consistent formatting across the application, propose a new function if you miss something
- Always try to use Vue Components first. If you need create a new to deduplicate code, suggest it and create it.
- **Reuse before creating**: Before creating new components or styling, always search for existing shared components (`src/components/`), shared CSS (`src/styles/shared-components.css`), and design tokens (`src/assets/design-tokens.css`) that can be reused or extended. Only create new shared components/styles when no suitable existing pattern exists. When you do create new shared patterns, add them to shared CSS or create a component — never leave reusable patterns only in scoped styles.

#### UI Consistency Rules
- **No Hardcoded Colors in CSS**: Never use hex color literals (`#f8f9fa`, `#28a745`, etc.) in `<style>` blocks. Always use CSS custom properties from `design-tokens.css` (e.g., `var(--color-light)`, `var(--color-success)`, `var(--color-danger)`, `var(--table-header-bg)`)
- **Use Design Token Shadows and Radii**: No literal `box-shadow:` or `border-radius:` values. Use `var(--shadow-*)` and `var(--radius-*)` or `var(--card-border-radius)`
- **Use Badge Component**: Never use raw `<span class="badge bg-*">`. Always use the `Badge.vue` component with appropriate `variant` and `size` props
- **Standard Table Pattern**: All data tables must use CSS classes `table table-sm table-hover mb-0` wrapped in `<div class="table-responsive">`. Use `SortableTableHeader` for sortable columns. Show `EmptyState` component when table data is empty
- **Three-State View Pattern**: Every async view must follow: `<LoadingState v-if="loading" />` → `<ErrorState v-else-if="error" />` → content. Tables within content show `<EmptyState>` when data is empty
- **Page Headers**: Use `layout/PageHeader.vue` for page-level headers. Use `MainCardHeader.vue` for card headers inside `MainCard`
- **Modals**: Use `GenericModal` with `v-model:show` for all modal dialogs. Do not create custom modal overlays
- **Single Token Source**: Only `design-tokens.css` may define `:root` CSS custom properties. No other file may declare `:root { ... }`

### Build Commands
- **Java Version**: `sdk use java 25.0.1-amzn`
- **Backend Compile**:
  ```bash
  JAVA_HOME=/Users/petrbouda/.sdkman/candidates/java/25.0.1-amzn /Users/petrbouda/.sdkman/candidates/maven/current/bin/mvn clean compile
  ```
- **Frontend Dev**: `cd jeffrey-microscope/pages-microscope && npm run dev`
- **Frontend Build**: `cd jeffrey-microscope/pages-microscope && npm run build`
- **Frontend Lint**: `cd jeffrey-microscope/pages-microscope && npm run lint`
- **Frontend Format**: `cd jeffrey-microscope/pages-microscope && npm run format`
- **Frontend Test**: `cd jeffrey-microscope/pages-microscope && npm run test`
- **Frontend Protobuf**: `cd jeffrey-microscope/pages-microscope && npm run proto:generate`

## API Structure
- **jeffrey-microscope REST**: `/api/internal/` for frontend-facing APIs — resources in `jeffrey-microscope/core-microscope/.../resources/`
- **Profile REST**: `/api/internal/profiles/{profileId}/` for profile features — resources in `jeffrey-microscope/profiles/profile-management/.../resources/`
- **jeffrey-server REST**: `/api/internal/` for minimal server UI — resources in `jeffrey-server/core-server/.../resources/`
- **gRPC**: Remote workspace communication between jeffrey-microscope and jeffrey-server — proto definitions in `shared/server-api/src/main/proto/jeffrey/api/v1/`, service implementations in `jeffrey-server/core-server/.../grpc/`, clients in `jeffrey-microscope/core-microscope/.../client/`
- gRPC proto files: `workspace_service.proto`, `project_service.proto`, `instance_service.proto`, `recording_download_service.proto`, `repository_service.proto`, `profiler_settings_service.proto`, `messages_service.proto`
- gRPC clients: `RemoteClients` record containing `RemoteDiscoveryClient`, `RemoteRepositoryClient`, `RemoteRecordingStreamClient`, `RemoteProfilerClient`, `RemoteMessagesClient`, `RemoteInstancesClient`, `RemoteProjectsClient`
- Implemented using Jersey/JAX-RS (not Spring MVC) for REST
- JSON data exchange format for REST, Protobuf for gRPC
- Multi-part file uploads for JFR files

## Git Commits
- Never add `Co-Authored-By: Claude` or any AI co-author trailer to commit messages
- Never automatically commit, create tags, or push. These actions happen **only** when the user explicitly asks for them ("commit", "tag", "push", "ship it", etc.). Finishing a feature, passing tests, or a clean build is **not** a trigger to commit — stop at the working-tree change and wait.
- **Authorization is per-change-set, not standing.** A "commit and push" approval applies only to the diff in front of you at that moment. The next request — even immediately after, even for a closely-related follow-up — needs a fresh confirmation. Do not treat one OK as a session-wide pass.

## Development Workflow
1. Backend development in Java with Spring Boot (two deployment targets: microscope and server)
2. Frontend development with Vue 3 and TypeScript (primary UI in `jeffrey-microscope/pages-microscope/`)
3. Integration through REST APIs (microscope) and gRPC (server communication)
4. Docker containerization for deployment
5. Maven for Java build management, npm for frontend dependencies

## Testing
- **Backend**: JUnit 5 tests, with nested JUnit classes to group logical parts
- **Backend**: Mockito for mocking dependencies
- **Backend**: `@DuckDBTest` custom annotation for database integration tests (from `shared/test`)
- **Backend**: Use `java.time.Clock` instead of real timestamps to fix time
- **Backend gRPC**: Every gRPC service must have an in-process integration test using `InProcessServerBuilder`/`InProcessChannelBuilder` (`grpc-inprocess` dependency). Tests should cover validation errors (status codes), and end-to-end streaming with real data where applicable. See `EventStreamingGrpcServiceTest` for the reference pattern.
- **Backend Async Assertions**: Use Awaitility (`org.awaitility:awaitility`) for async/polling assertions instead of hand-rolled `Thread.sleep` loops. Example: `await().atMost(5, SECONDS).untilAsserted(() -> assertEquals("expected", getResult()));`
- **Frontend**: Vitest (`cd jeffrey-microscope/pages-microscope && npm run test`)

## AI Integration
- Spring AI 2.0.0-M3 with Claude and OpenAI providers
- AI modules: `jeffrey-microscope/profiles/ai-config/`, `jeffrey-microscope/profiles/oql-assistant/`, `jeffrey-microscope/profiles/duckdb-ai-mcp/`, `jeffrey-microscope/profiles/heap-dump-ai-mcp/`
- Config: `jeffrey.ai.provider=claude`, `jeffrey.ai.model=claude-opus-4-6`

## DuckDB MCP Servers
- You can use MCP Server to connect to DuckDB database to get information about the current data

### Structure of the Database
- Three-tier architecture: microscope core database, server database, and per-profile databases (isolated)
- Local Core DB: workspaces, projects, recordings, remote workspace connections
- Server DB: server-side workspaces, projects, scheduling
- Profile DB: events, flamegraph data, analysis results for a single profile
- `profile_id` gathers all data related to a specific profile

### Database Schema
- Microscope Core migrations: `jeffrey-microscope/microscope-core-sql-persistence/src/main/resources/db/migration/microscope/core/V001__init.sql`
- Server migrations: `jeffrey-server/server-sql-persistence/src/main/resources/db/migration/server/V001__init.sql`
- Profile migrations: `jeffrey-microscope/profiles/profile-sql-persistence/src/main/resources/db/migration/profile/V001__init.sql`
- **Migration policy**: Never create new migration files (V002, V003, etc.). Always modify the existing V001 file directly. The database is recreated from scratch on each startup.
- JFR Event Types reference: https://sap.github.io/jfrevents/ (select Java version for event details)
- JSONB `fields` column in the `events` table contains event-specific data — see `/jfr-event-fields` skill for full field reference per event type

## Documentation Sync (Jeffrey Pages)

When modifying code, keep the corresponding documentation pages in `jeffrey-pages/` up to date. The docs are organized by domain:

| Code module | Documentation pages |
|---|---|
| `jeffrey-microscope/core-microscope` | `jeffrey-pages/src/views/docs/platform/` — workspaces, projects, recordings, sessions, profiler settings, alerts |
| `jeffrey-server/core-server` | `jeffrey-pages/src/views/docs/platform/` — scheduler |
| `jeffrey-microscope/profiles/profile-management` | `jeffrey-pages/src/views/docs/profiles/` — visualization, application analysis, JVM internals, heap dump analysis |
| `jeffrey-cli/` | `jeffrey-pages/src/views/docs/cli/` — CLI overview, configuration, directory structure, generated output |
| Architecture changes | `jeffrey-pages/src/views/docs/architecture/` — overview, public API, storage |
| Deployment changes | `jeffrey-pages/src/views/docs/deployments/` — JAR, container, live recording |

## License
GNU Affero General Public License v3.0 (AGPL-3.0)
