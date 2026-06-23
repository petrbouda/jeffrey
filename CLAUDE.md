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
- **Remote Communication**: gRPC (proto files in `shared/hub-api/`)
- **CLI**: GraalVM Native Image

### Deployment Architecture

The project supports two deployment modes: **jeffrey-microscope** (standalone) and **jeffrey-hub** (multi-workspace server). They share common modules via **shared/**.

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
│  HubApplication — multi-workspace server with scheduling      │
│                                                                  │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │  core-hub  │  │  pages-hub     │  │  shared/          │  │
│  │  gRPC services│  │  Minimal Vue 3    │  │  persistent-queue │  │
│  │  scheduler    │  │  UI               │  │                   │  │
│  └──────────────┘  └──────────────────┘  └──────────────────┘  │
│                                                                  │
│  Persistence: hub-sql-persistence (server DB)                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        SHARED                                    │
│  Common utilities, persistence abstractions, test infrastructure │
│  gRPC proto definitions, storage, SQL builder                    │
│  Modules: common, persistence, test, hub-api, sql-builder,   │
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
- `claude-code-headless` — Claude Code (headless) AI backend + reusable MCP tool machinery (`ReflectiveToolset`)

**jeffrey-hub** (`jeffrey-hub/`):
- `core-hub` — Main Spring Boot app (HubApplication), gRPC service implementations, scheduler/jobs, JFR streaming
- `hub-persistence-api` — Persistence interfaces for server domain
- `hub-sql-persistence` — DuckDB persistence for server (workspaces, projects, scheduling)
- `pages-hub` — Minimal Vue 3 frontend
- `shared/persistent-queue` — Server-specific persistent queue

**shared** (`shared/`):
- `common` — Shared utilities and models
- `persistence` — Common persistence abstractions
- `test` — Test infrastructure (`@DuckDBTest` annotation, test utilities)
- `hub-api` — gRPC proto files at `src/main/proto/jeffrey/api/v1/`
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
│   │       ├── client/                # gRPC clients (HubClients, Remote*Client)
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
├── jeffrey-hub/                    # Multi-workspace server deployment
│   ├── core-hub/                   # Main Spring Boot app (HubApplication)
│   │   └── src/.../server/core/
│   │       ├── grpc/                  # gRPC service implementations
│   │       ├── scheduler/             # Job scheduler, job definitions
│   │       │   └── job/               # Job implementations + descriptor/
│   │       ├── resources/             # REST resources (WorkspacesResource, GrpcDocsResource)
│   │       └── streaming/             # JFR streaming
│   ├── hub-persistence-api/        # Server persistence interfaces
│   ├── hub-sql-persistence/        # Server DuckDB persistence
│   ├── pages-hub/                  # Minimal Vue 3 frontend
│   └── shared/                        # Server-specific shared modules
│       └── persistent-queue/          # Server persistent queue
├── shared/                            # Shared modules (used by both deployments)
│   ├── common/                        # Common utilities and models
│   ├── persistence/                   # Common persistence abstractions
│   ├── test/                          # Test infrastructure (@DuckDBTest)
│   ├── hub-api/                    # gRPC proto definitions
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
│   ├── build-hub/                  # Server application assembly
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

### Design over micro-optimization (default mode)
Default to clean object-oriented design — sealed type hierarchies, focused single-responsibility collaborators, composition over inheritance, polymorphism over conditionals — even when the result is more files or a small amount of extra indirection. Examples that count as "design over optimization": splitting a god class into a sealed analysis hierarchy plus collaborator services; preferring a `Map<K, V>` lookup over a manually hand-rolled switch ladder; introducing a small record over passing five parallel parameters.

Do **not** sacrifice design for low-level optimizations — hot-path tuning, manual inlining, allocation elimination, primitive-array packing, lock-free tricks, parallel execution, pre-computed lookup caches, or similar — unless the user **explicitly** asks for the optimization (e.g., "make this faster", "reduce allocations here", "optimize the hot path"). The price of those optimizations is usually paid in code clarity, test isolation, and refactor cost; that price is only worth paying when the user has named it as their goal.

**Evident trade-offs are surfaced, not decided silently.** If, while doing design work, you spot a meaningful optimization that would cost design clarity — e.g., a hot-path tightening that requires inlining a sealed type away, an allocation reduction that needs a primitive-array shape, a parallel pipeline that needs shared mutable state, a lookup table that replaces a polymorphic dispatch — **don't pick on the user's behalf**. Present both options with one sentence each on the design cost and the optimization win, and let the user choose. The skill is "make the trade-off legible," not "default-pick design and hide the alternative."

When unsure whether a request is "make it cleaner" or "make it faster", ask. Default mode is design.

### General (applies to Java, TypeScript, Vue, JS)
- **Always use braces for control flow**: Every `if`, `else`, `else if`, `for`, `while`, and `do-while` body must be wrapped in `{ ... }` braces — even when the body is a single statement. Never write the inline single-line form. This applies to early-return guards, null checks, instanceof guards, loops, and every other branching construct, in **both Java and TypeScript/Vue**.

  ```java
  // good
  if (segments.isEmpty()) {
      return false;
  }

  // bad
  if (segments.isEmpty()) return false;
  ```

  ```ts
  // good
  if (!run.result) {
    return [];
  }

  // bad
  if (!run.result) return [];
  ```

  Same rule for `else`, `else if`, `for`, `while`. If the body is empty, use `{ }` not `;`. This is non-negotiable — the goal is consistent diff-friendly bodies and to eliminate the "dangling-statement" foot-gun.

### Java Backend
- **Package Structure**: `cafe.jeffrey.microscope.*` for microscope deployment, `cafe.jeffrey.hub.*` for server deployment, `cafe.jeffrey.profile.*` for profiles, `cafe.jeffrey.*` for shared modules
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
- **gRPC**: Proto files in `shared/hub-api/` (package `cafe.jeffrey.hub.api.v1`), implementations in `jeffrey-hub/core-hub/.../grpc/`, clients in `jeffrey-microscope/core-microscope/.../client/`
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
- **No inline string/number literals in logic**: Any string or magic number that gets matched, compared, concatenated into SQL, or used as a configuration value must live in a `private static final` constant with a descriptive name. Inline literals are only acceptable for one-off values that are obvious from their immediate context (e.g., `Math.max(0, x)`, `LIMIT 1`). This includes SQL keywords, column-name aliases, row caps, timeouts, and error-message tokens.
- **No chained `equals` ladders for set-membership tests**: When checking whether a value is one of several alternatives, never write `s.equals("a") || s.equals("b") || s.equals("c")`. Declare the alternatives in a `private static final Set<String>` constant and use `SET.contains(s)`. Same for `Set<Integer>`, enum sets, etc. Keeps the alternatives visible at the top of the file, makes adding a new alias a one-line change, and reads better at the call site.

  ```java
  // good
  private static final Set<String> RETAINED_SIZE_COLUMN_ALIASES = Set.of("retained_size", "retained", "bytes");
  if (RETAINED_SIZE_COLUMN_ALIASES.contains(label)) { ... }

  // bad
  if (label.equals("retained_size") || label.equals("retained") || label.equals("bytes")) { ... }
  ```

### Frontend (Vue/TypeScript)
- **Components**: PascalCase for component names
- **Composition API**: Preferred over Options API
- **TypeScript**: Strict typing with interfaces for API models
- **Design Tokens**: CSS custom properties in `shared/ui/common/src/assets/design-tokens.css` (import as `@shared/assets/design-tokens.css`) — always use these for colors, spacing, typography
- **Composables**: Reusable reactive logic in `jeffrey-microscope/pages-microscope/src/composables/` (useModal, useNavigation, useAiAnalysis, useWorkspaceType, etc.)
- **API Clients**: Two base classes in `jeffrey-microscope/pages-microscope/src/services/api/`:
  - `BasePlatformClient` — for workspace/project APIs (used by WorkspaceClient, ProjectClient)
  - `BaseProfileClient` — for profile feature APIs (used by OqlAssistantClient, ProfileMethodTracingClient, etc.)
- **State Management**: Simple ref-based stores in `jeffrey-microscope/pages-microscope/src/stores/` (not Pinia)
- **Protobuf**: Used for flamegraph binary data; regenerate with `npm run proto:generate`
- **Styling**: Use shared CSS files first, then scoped CSS for component-specific styles
  - **Shared CSS files** (live in `shared/ui/common/src/styles/`, import via `@shared/...` or `@import` in SCSS):
    - `@shared/styles/shared-components.css` - Common UI patterns (search-container, cards, buttons, loading/empty states, drawer sections, form fields, info rows)
    - `@/assets/_sidebar-menu.scss` - Sidebar navigation styles (nav-item, nav-submenu, disabled-feature) — still app-local
  - Always check shared CSS files before adding new scoped styles
  - Add commonly reused styles to `@shared/styles/shared-components.css` to avoid duplication
- **File Organization**: Feature-based grouping with shared components
- **Timestamps**: All timestamps are UTC epoch millis (numbers). Never use `new Date()` for parsing or formatting — always use `FormattingService` methods. Never propagate date strings from the backend; always use numeric UTC timestamps. Frontend form inputs like `datetime-local` must be converted to/from epoch millis at the boundary.
- Formatting values use FormattingService, which provides consistent formatting across the application, propose a new function if you miss something
- **Shared UI modules** (consumed via Vite aliases, defined identically in every `pages-*` app):
  - `@shared` → `shared/ui/common/src` — generic components, services (FormattingService, BasePlatformClient, HttpUtils, ToastService), styles, and design tokens
  - `@workspaces` → `shared/ui/workspaces/ui` — remote-workspace/recording components + API clients
  - `@instances` → `shared/ui/instances/src` — instance views
- **Shared-first (MUST, non-negotiable)**: Before writing any new markup or component, you MUST first check the shared modules — `@shared` first, then `@workspaces`/`@instances` — for an existing component to use, compose, or extend, and check `@shared/assets/design-tokens.css` + `@shared/styles/shared-components.css` for existing styles. Only write custom markup or a new component when no shared one fits. Never duplicate a shared component locally.
- **Where a new component lives**: If it is **generic** (no page- or JFR-domain semantics — a chart, table, form input, badge, breadcrumb, layout container, modal, drawer, etc.), create it under `shared/ui/common/src/components/` (`@shared`), NOT app-local. An app's `src/components/` is reserved for components tied to a specific page/feature (profile analysis, flamegraph, heap, gc, jdbc, grpc, span, streaming, etc.). When unsure whether a component is generic, prefer `@shared`.

#### UI Consistency Rules
- **No Hardcoded Colors in CSS**: Never use hex color literals (`#f8f9fa`, `#28a745`, etc.) in `<style>` blocks. Always use CSS custom properties from `design-tokens.css` (e.g., `var(--color-light)`, `var(--color-success)`, `var(--color-danger)`, `var(--table-header-bg)`)
- **Use Design Token Shadows and Radii**: No literal `box-shadow:` or `border-radius:` values. Use `var(--shadow-*)` and `var(--radius-*)` or `var(--card-border-radius)`
- **Use Badge Component**: Never use raw `<span class="badge bg-*">`. Always use the `Badge.vue` component with appropriate `variant` and `size` props
- **Standard Table Pattern**: All data tables must use the `components/table/DataTable.vue` family — `DataTable` (card wrapper that renders `table table-sm table-hover mb-0` inside `.table-responsive`) with its `#toolbar` slot (`TableToolbar` — `v-model` search + `#filters`), default slot (`<thead>`/`<tbody>`), and `#footer` slot (`TableShowMore` for pagination). Use `SortableTableHeader` for sortable columns and render `EmptyState` as a sibling when there is no data. Do not hand-roll `<div class="table-responsive"><table>`. Scaffold with the `/data-table` skill; reference `views/profiles/detail/ProfileThreadDumps.vue`
- **Three-State View Pattern**: Every async view must follow: `<LoadingState v-if="loading" />` → `<ErrorState v-else-if="error" />` → content. Tables within content show `<EmptyState>` when data is empty
- **Page Headers**: Use `layout/PageHeader.vue` for page-level headers. Use `MainCardHeader.vue` for card headers inside `MainCard` (props `icon`, `title`, `:badge?`, `#actions` slot). Scaffold new pages with the `/global-page` skill
- **Modals**: Use `GenericModal` with `v-model:show` for all modal dialogs — never a custom overlay. Pick `size` by content: `md` simple forms · `lg` lists/single column · `xl` rich/two-column. For large editors use the **wide near-fullscreen** pattern: `modal-dialog-class="<name> events-modal-dialog modal-dialog-centered"` plus a scoped `:deep(.modal-dialog.<name>) { max-width: none; width: calc(100vw - 3.5rem); }` (equal gutters, body scrolls as one unit, footer pinned). Scaffold with the `/new-modal` skill; reference `views/global/GuardiansView.vue`
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
- **jeffrey-hub REST**: `/api/internal/` for minimal server UI — resources in `jeffrey-hub/core-hub/.../resources/`
- **gRPC**: Remote workspace communication between jeffrey-microscope and jeffrey-hub — proto definitions in `shared/hub-api/src/main/proto/jeffrey/api/v1/`, service implementations in `jeffrey-hub/core-hub/.../grpc/`, clients in `jeffrey-microscope/core-microscope/.../client/`
- gRPC proto files: `workspace_service.proto`, `project_service.proto`, `instance_service.proto`, `recording_download_service.proto`, `repository_service.proto`, `profiler_settings_service.proto`, `messages_service.proto`
- gRPC clients: `HubClients` record containing `DiscoveryClient`, `RepositoryClient`, `RecordingStreamClient`, `ProfilerClient`, `RemoteMessagesClient`, `InstancesClient`, `ProjectsClient`
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
- Config: `jeffrey.ai.provider=claude`, `jeffrey.ai.model=claude-opus-4-8`

## DuckDB MCP Servers
- You can use MCP Server to connect to DuckDB database to get information about the current data

### Structure of the Database
- Three-tier architecture: microscope core database, server database, and per-profile databases (isolated)
- Local Core DB: workspaces, projects, recordings, remote workspace connections
- Server DB: server-side workspaces, projects, scheduling
- Profile DB: events, flamegraph data, analysis results for a single profile
- `profile_id` gathers all data related to a specific profile

### On-disk Database Locations (for direct inspection)
- Per-profile DB file: `~/.jeffrey-microscope/profiles/<profile-id>/profile-data.db` (contains the `events`, `threads`, `event_types`, etc. tables for that profile)
- Microscope core DB file: `~/.jeffrey-microscope/jeffrey-data.db`
- A running app holds an exclusive lock on these files, so a read-only open fails with `Could not set lock on file`. To inspect while the app runs, copy the file first (e.g. `cp .../profile-data.db /tmp/probe.db`, plus `.wal` if present) and open the copy read-only.
- No `duckdb` CLI or python module is installed by default; quickest path is a throwaway venv: `python3 -m venv /tmp/ddbvenv && /tmp/ddbvenv/bin/pip install duckdb`, then `duckdb.connect(path, read_only=True)`.

### Database Schema
- Microscope Core migrations: `jeffrey-microscope/microscope-core-sql-persistence/src/main/resources/db/migration/microscope/core/` — `V001__init.sql` (table schema) + `V002__guardians_seed.sql` (built-in Guardian guard seed data)
- Server migrations: `jeffrey-hub/hub-sql-persistence/src/main/resources/db/migration/server/V001__init.sql`
- Profile migrations: `jeffrey-microscope/profiles/profile-sql-persistence/src/main/resources/db/migration/profile/V001__init.sql`
- **Migration policy**: Keep table schema (`CREATE TABLE`) in `V001__init.sql` and edit it in place for schema changes. Seed data may live in a separate, purpose-named migration (e.g. `V002__guardians_seed.sql`) to keep schema and data concerns separated. The database is recreated from scratch on each startup, so editing these in development is safe.
- JFR Event Types reference: https://sap.github.io/jfrevents/ (select Java version for event details)
- JSONB `fields` column in the `events` table contains event-specific data — see `/jfr-event-fields` skill for full field reference per event type

## Documentation Sync (Jeffrey Pages)

When modifying code, keep the corresponding documentation pages in `jeffrey-pages/` up to date. The docs are organized by domain:

| Code module | Documentation pages |
|---|---|
| `jeffrey-microscope/core-microscope` | `jeffrey-pages/src/views/docs/platform/` — workspaces, projects, recordings, sessions, profiler settings, alerts |
| `jeffrey-hub/core-hub` | `jeffrey-pages/src/views/docs/platform/` — scheduler |
| `jeffrey-microscope/profiles/profile-management` | `jeffrey-pages/src/views/docs/profiles/` — visualization, application analysis, JVM internals, heap dump analysis |
| `jeffrey-cli/` | `jeffrey-pages/src/views/docs/cli/` — CLI overview, configuration, directory structure, generated output |
| Architecture changes | `jeffrey-pages/src/views/docs/architecture/` — overview, public API, storage |
| Deployment changes | `jeffrey-pages/src/views/docs/deployments/` — JAR, container, live recording |

## License
GNU Affero General Public License v3.0 (AGPL-3.0)
