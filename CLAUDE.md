# Jeffrey — Performance Analyst

Self-hosted performance analyst for the JVM: ingests JFR recordings and heap dumps (pprof/OTLP too) and turns them into flamegraphs, timeseries, heap analysis and JVM dashboards, plus an MCP server so a coding agent can read the same profiles.

Three deployments, released separately: **jeffrey-microscope** (single-user analysis app; GitHub release on a `v<version>` tag — `release.yml`), **jeffrey-provisioner** (GraalVM native CLI that provisions a profiled JVM; its own GitHub release by a manual run of `release-provisioner.yml`, tagged `provisioner-v<version>`) and **volume-scheduler** (a small maintenance service beside it on the shared recording volume: retention and compression jobs, a JSON API under `/api/v1` and no UI; no API to Microscope; only its Docker image, by a manual run of `release-volume-scheduler.yml`, tagged `volume-scheduler-v<version>`). Every product has its own GitHub release so the jeffrey-pages version banners can discover them from the Releases API: **jeffrey-jib** (`jib-v<version>`, `release-jib-extension.yml`) and **jeffrey-intellij-plugin** (`intellij-plugin-v<version>`, `release-intellij-plugin.yml`). All non-Microscope releases use `--latest=false` so Microscope keeps the "latest" designation. Java 25 · Spring Boot 4 · DuckDB · Vue 3 + TypeScript · Maven + Vite.

Detailed conventions live in `.claude/rules/` and load only when you touch matching files: `backend-java`, `frontend-vue`, `test-patterns`, `volume-scheduler`, `mcp-server`, `recording-parsing`, `intellij-plugin`, `docs-sync`. This file holds only what applies to every session.

## Repository map

```
jeffrey-microscope/                    MicroscopeApplication (core), Vue SPA (pages; its generic
                                       components, services and design tokens in src/shared, imported as @shared)
  core/                     REST /api/internal/** (every controller), managers, MCP endpoint and tools
                                       (…/core/mcp/), the recordings store (core.manager.recordings: Recording,
                                       RecordingFile, Bundle, BundleFile, RecordingStorageLayout) and downloads
                                       (core.manager.download); the profile model types that only core uses
                                       (core.profile.model: Type, ProfileInfo, EventTypeName, EventSummary, GraphType,
                                       WeightUnit, FrameResolutionMode, StacktraceType, StacktraceTag, SpanScope,
                                       SpanInterval, SpanConventionKeys, ProfilingStartEnd, ThreadInfo, HiddenClassName,
                                       EventSubtype, SearchPattern, FrameType; core.profile.model.time: TimeRange and
                                       its subtypes; core.profile.settings: ActiveSetting, ActiveSettings),
                                       general-purpose utilities (core.utils: Json, BestEffortFiles, Schedulers,
                                       IDGenerator, BytesUtils, DurationUtils, CacheKey, TempDirFactory,
                                       SemanticVersion, StringUtils),
                                       ErrorResponse in core.web, Lz4Decompressor in core.profile.ingest;
                                       the profile analysis, one package tree per feature under core.profile (flamegraph,
                                       frameir, timeseries, subsecond, thread, gc, memory, custom events, heap-dump
                                       orchestration, …) and the JFR model it reads (core.profile.jfr: the JFR model
                                       types; core.profile.{event,exceptions,analysis,treetable,graph}),
                                       the init pipelines (core.pipeline) and their long-running
                                       operation handles (core.operation), Jeffrey's own jeffrey.Notification
                                       events (core.notification), the recording parsers (core.profile.parser: JFR via the JDK and
                                       raw chunks, pprof, OTLP), heap dumps (core.profile.heapdump, OQL in .oql), the
                                       per-profile DuckDB (core.profile.persistence: repository API + impl), the core
                                       DuckDB of recordings and profiles (core.persistence) and the DuckDB plumbing every
                                       Microscope database uses (core.duckdb: data sources, pools, DatabaseManager,
                                       appenders, metrics, the SQL builder in core.duckdb.sql);
                                       test support in src/test: @DuckDBTest (core.test), RecordedSession,
                                       TestappRecording and CheckoutRecording (core.test.recording)
  connectors/
    connector-api/                     what Microscope asks of any connector, with no storage behind it: the client
                                       interfaces and DTOs (connector.api), the managers over them (api.manager) and
                                       the connector kinds as ServiceLoader providers (connector.api.spi:
                                       ConnectorProvider, DeclaredConnector whose open() hands back the clients,
                                       found through module-info provides + META-INF/services); core
                                       compiles against this module only, so a non-volume kind needs nothing else
    volume-connector/                  the connector over a recording volume (writable or read-only): the clients
                                       (connector.volume, over a VolumeSource) and the local kind (connector.local:
                                       LocalDirectoryAddress, FilesystemVolume kept inside its root, no link out,
                                       read live; for testing and copied trees); core takes it at run time only
    azure-files-connector/             the Azure file share kind (AzureFilesAddress, its provider and settings;
                                       SMB + NFS, SAS or Entra) over volume-connector's clients, a module of its own
                                       so the Azure SDK stays out of everything else; core takes it at
                                       run time only
  common/                   what every Microscope module shares, no third-party dependency of its own
                                       (only shared/volume, for JeffreyLayout): the utilities — JeffreyVersion,
                                       InstantUtils; the exception hierarchy (ErrorCode, ErrorType, Exceptions,
                                       JeffreyException and subtypes); FileSystemUtils, TempDirectory; Measuring,
                                       Elapsed — and the domain records two or more modules share, no Jackson or JFR
                                       (common.model: ProjectInfo, ProjectInstanceInfo, RecordingEventSource,
                                       ProfileKind; ConnectorAddress and ConnectorKind in model.connector;
                                       WorkspaceInfo/Status/Location in model.workspace; the repository API surface
                                       in model.repository; the imported recordings' file kinds (ManagedFile,
                                       FileCategory, FileExtensions) in model.recording; file matchers in
                                       model.repository.matcher)
  mcp-server/                          one MCP module: the MCP 2026-07-28 protocol types + SPI (tool/result/outcome
                                       records, closed schema tooling, cursor, extensions) and Jeffrey's @Tool adapter
                                       over them (ReflectiveToolset/ProfileScopedToolset/ToolMethodIndex, the
                                       ToolInvocation span, hints, findings); the modern Spring AI server + bridge live in core
volume-scheduler/                      one module: VolumeSchedulerApplication (cafe.jeffrey.volumescheduler: config/ (typed
                                       jeffrey.volume-scheduler.* properties), volume/, scheduler/, retention/, compression/,
                                       storage/, web/ (the REST API: /api/v1 storage and jobs)) — no database, no UI:
                                       the volume (jeffrey.volume-scheduler.volume.path, required) is its only state
shared/                                only what Microscope, the volume scheduler and the provisioner must agree on to
                                       read one volume: volume (its names — JeffreyLayout, SessionIds — and its layout
                                       read through a storage port: folder names are the state:
                                       projects/instances/sessions/files; the volume scheduler's reader; plus
                                       VolumeWriter, the one writer of a volume).
                                       No utilities: each product keeps its own (Microscope's in common
                                       and core's core.utils)
jeffrey-provisioner/                   GraalVM native CLI that provisions a profiled JVM: session directories and
                                       the argfile that starts it under async-profiler (profiler-path)
jeffrey-agent-plugin/                  the "microscope" coding-agent plugin: skills, agents, manifests for Claude Code /
                                       Codex / Gemini / Agent Plugins (manifests rendered from build/plugin/manifests/)
jeffrey-intellij-plugin/               standalone Gradle project (Java 21), links to Microscope, never renders profiles
jeffrey-pages/                         documentation site — keep in sync (see docs-sync rule)
utilities/                             release root (jeffrey-utilities-parent + jreleaser.yml, release-utilities.yml) of two
                                       independent parts: jeffrey-events and jeffrey-tracing-parent:
                                       tracing API on Java 21 + two span storages picked by ServiceLoader —
                                       jeffrey-tracing-scoped-value (ScopedValue, 25, opt-in) and
                                       jeffrey-tracing-thread-local (21, the starter's) — + instrumentation
jeffrey-jib/                           JIB extensions + payloads: provisioner, async-profiler (release-jib-extension.yml)
build/                                 build-microscope (microscope.jar — Microscope's only distribution, no container
                                       image), build-volume-scheduler-native (GraalVM; the volume scheduler's only
                                       distribution, docker/Dockerfile-volume-scheduler), build-provisioner(-native)
```

## Build, run, verify

```bash
export JAVA_HOME=/home/pbouda/.sdkman/candidates/java/25.0.1-amzn   # default `java` on PATH is 26
MVN=/home/pbouda/.sdkman/candidates/maven/current/bin/mvn
$MVN -q compile                                    # whole reactor (runs the frontend build too)
$MVN -q -pl volume-scheduler -am test              # one module — -am is required, siblings are not in ~/.m2

cd jeffrey-microscope/pages
npm run dev | build | test | typecheck | format    # build = vue-tsc + vite; `npm run lint` is currently broken
npm run proto:generate                             # after editing flamegraph.proto (also update ProtobufConverter)

./run-microscope.sh [--clean]                      # Microscope under async-profiler, data in ~/.jeffrey-microscope
```

Verification agents: `java-compiler`, `frontend-builder`, `test-runner`, `design-token-compliance`, `api-contract-reviewer`, `security-reviewer` (MCP, uploads, SQL, volume paths), `docs-sync-checker`.

### Inspecting DuckDB files
- Per-profile DB `~/.jeffrey-microscope/profiles/<profile-id>/profile-data.db` (`events`, `threads`, `event_types`, …); core DB `~/.jeffrey-microscope/jeffrey-data.db`.
- A running app holds an exclusive lock: **copy the file first** (plus `.wal` if present) and open the copy read-only. No `duckdb` CLI is installed — `python3 -m venv /tmp/ddbvenv && /tmp/ddbvenv/bin/pip install duckdb`, then `duckdb.connect(path, read_only=True)`.
- Schema is `CREATE TABLE` in each `V001__init.sql` (`core/src/main/resources/db/migration/`: `microscope/core` for the core DB, `profile` per profile, `heap-dump-index`); edit it in place — there are no migrations and no backward compatibility: Flyway validates the core DB on startup, so an existing data dir is wiped with `./run-microscope.sh --clean` after a schema change. JFR event fields reference: https://sap.github.io/jfrevents/

## Rules that apply everywhere

- **Braces on every control-flow body**, Java and TypeScript, even one statement. Non-negotiable.
- **Design over micro-optimization.** Sealed hierarchies, small records, polymorphism over switch ladders, composition over inheritance — even at the cost of more files. Never trade clarity for a hot-path trick unless the user asks; when you see such a trade-off, state both options in a sentence each and let the user choose. Unsure whether "cleaner" or "faster" is wanted → ask.
- **Spring:** constructor injection only; no `@Component`/`@Service`/`@Repository`/`@Controller`/`@Autowired`. Only `@RestController` (MVC controllers) and `@ControllerAdvice` (`JeffreyExceptionHandler`) are allowed; everything else is an explicit `@Bean`.
- **Time:** inject `java.time.Clock`, never `Instant.now()`; elapsed time via `Measuring`. Frontend timestamps are UTC epoch millis, formatted only by `FormattingService`.
- **Literals:** anything matched, compared, put in SQL or used as config is a named `private static final`; set membership is `Set.of(...).contains`, not an `equals` ladder.
- **Annotations** on classes/fields/methods go on their own line; always `import`, never an inline FQCN; Apache-2.0 header (2026) on every Java file; SLF4J `"what happened: k1={} k2={}"` without commas.
- **Frontend shared-first:** check `@shared` first, then the app's own `src/components`, and the design tokens / `shared-components.css` before writing any markup; no hex colors, literal shadows or radii; `DataTable`, `GenericModal`, `Badge`, `PageHeader`/`MainCardHeader`, three-state (`LoadingState` → `ErrorState` → content, `EmptyState`) are mandatory. Generic components go to `pages/src/shared` (`@shared`).
- **Records** for DTOs and for any 3+ parameters or callbacks that travel together; validate in compact constructors with standard exceptions; domain code never depends on Spring types — map at the boundary.

## Architecture invariants (never)

- **Microscope reaches exactly one connector** — the recording volume on an Azure file share (`azure-files.*`) or in a local directory (`local.*`), one block of the two — declared in configuration only (`jeffrey.microscope.connector.*`), read at startup and never stored. The UI and the MCP tools show it; nothing connects, changes or disconnects one.
- **The volume scheduler has no API to Microscope.** It runs its two jobs on the volume (retention, compression) and serves only its own JSON API (`/api/v1`: storage and jobs) to operators — no UI; Microscope never talks to it. Never add a gRPC or REST channel between them — both read the volume through `shared/volume`.
- **The volume scheduler and Microscope share no domain type.** Each maps `shared/volume`'s layout types onto its own model (`common`'s `common.model`; the volume scheduler's `cafe.jeffrey.volumescheduler.volume` package); `ModuleBoundaryTest` on both sides fails on a cross-import. `shared/` gets no domain record and no utility one product could keep itself: the volume scheduler writes the LZ4 archives and Microscope reads them, each with its own code over the standard frame format.
- **MCP belongs to Microscope only** (`POST /api/mcp`, its only path). Never add an MCP endpoint or a JFR reader/log parser to the volume scheduler — Microscope pulls chunks off the volume and reads them itself.
- **Jeffrey never calls a model provider.** The only AI integration is the MCP server an outside agent calls into; a feature that would put a model inside Jeffrey is a skill or a tool instead.
- **A recording's files are never joined**; a download is an unbroken run of chunks, pulled one file per call; compression happens in the volume scheduler's compression job, never on a read path.
- **Artifacts (logs, crash files, dumps) are handed to the agent as a path**, not parsed server-side and not catalogued.
- **The IntelliJ plugin never renders profile data** beyond the recording panel's four figures + findings; everything else links to Microscope.
- Counts and sets repeated in prose are pinned by tests (`McpToolsetAssemblerTest`: 114 tools and the ten writers; `ProfileRouteManifestTest`); update the prose, never loosen the test.

## Testing

JUnit 5 with `@Nested`, Mockito, `@DuckDBTest` from `core`'s `cafe.jeffrey.microscope.core.test`, `Clock.fixed`, Awaitility for anything async. Frontend: Vitest.

## Git

- Never commit, tag or push unless the user explicitly asks in that message; a clean build or green tests is not a trigger. Authorization is per change-set — the next request needs a fresh one.
- No `Co-Authored-By: Claude` or any AI trailer, including in the commit a squash-merge produces (GitHub adds one when the squashed commits have another author — pass an explicit message and check the merged commit).

## Documentation

`jeffrey-pages/` documents every user-visible feature; the `docs-sync` rule maps modules to pages and lists the two registrations a new page needs. Update docs with the code change.

## License

Apache-2.0 — header text in `LICENSE_HEADER`.
