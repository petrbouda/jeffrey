# Jeffrey — Performance Analyst

Self-hosted performance analyst for the JVM: ingests JFR recordings and heap dumps (pprof/OTLP too) and turns them into flamegraphs, timeseries, heap analysis and JVM dashboards, plus an MCP server so a coding agent can read the same profiles.

Two deployments, one release: **jeffrey-microscope** (single-user analysis app) and **jeffrey-hub** (multi-workspace server that collects recordings; talks to Microscope over gRPC). Java 25 · Spring Boot 4 · DuckDB · Vue 3 + TypeScript · Maven + Vite.

Detailed conventions live in `.claude/rules/` and load only when you touch matching files: `backend-java`, `frontend-vue`, `grpc-proto`, `test-patterns`, `hub-repository`, `mcp-server`, `recording-parsing`, `intellij-plugin`, `docs-sync`. This file holds only what applies to every session.

## Repository map

```
jeffrey-microscope/                    MicroscopeApplication (core-microscope), Vue SPA (pages-microscope)
  core-microscope/                     REST /api/internal/**, managers, MCP endpoint (…/core/mcp/)
  microscope-model/                    Microscope's domain records (cafe.jeffrey.microscope.model)
  microscope-core-{persistence-api,sql-persistence}/   microscope core DuckDB (recordings, profiles, hubs)
  grpc-client/ + hub-client/           gRPC clients, aggregated by the HubClients record
  recordings-core/ recording-storage-api/ notifications/
  ui-hubs/                             hub browser: REST controllers + Vue module (@hubs)
  profiles/                            analysis modules: profile-management (features + REST), recording-parser/
                                       (jfr-parser-api, jdk-jfr-parser, raw-jfr-parser, otlp-parser, pprof-parser),
                                       profile-{persistence-api,sql-persistence}, flamegraph, timeseries, subsecond,
                                       profile-threads, profile-gc, profile-memory, profile-custom-events, frame-ir,
                                       heap-dump, heapdump-oql, profile-heapdump-orchestration, common-profile,
                                       mcp-server (the MCP protocol layer)
jeffrey-hub/                           HubApplication (core-hub: gRPC services, scheduler/jobs, web/), hub-model,
                                       hub-{persistence-api,sql-persistence}, pages-hub (minimal Vue UI)
shared/                                common (utilities + hub↔provisioner contract types only), persistence,
                                       sql-builder, test (@DuckDBTest), hub-api (protos only), pending-index,
                                       ui/common (@shared: generic components, services, design tokens), ui/version
jeffrey-provisioner/                   GraalVM native CLI that provisions a profiled JVM (no Java agent;
                                       liveness comes from utilities/jeffrey-heartbeat-parent, declared by heartbeat.enabled)
jeffrey-claude-plugin/                 the "microscope" plugin: skills, agents, manifests for Claude Code / Codex / Gemini
jeffrey-intellij-plugin/               standalone Gradle project (Java 21), links to Microscope, never renders profiles
jeffrey-pages/                         documentation site — keep in sync (see docs-sync rule)
utilities/                             release root (jeffrey-utilities-parent + jreleaser.yml, release-utilities.yml) of three
                                       independent parts: jeffrey-events, jeffrey-heartbeat-parent
                                       (library + starter), jeffrey-tracing-parent:
                                       tracing API on Java 21 + two span storages — jeffrey-tracing (ScopedValue, 25)
                                       and jeffrey-tracing-thread-local (21), picked by ServiceLoader — + instrumentation
jeffrey-jib/                           JIB extensions + provisioner payloads (release-jib.yml)
build/                                 build-microscope, build-hub, *-jib, build-provisioner(-native), build-agent-tests
stubs/                                 jeffrey-hub-stub, outside the reactor (run-hub-stub.sh)
```

## Build, run, verify

```bash
export JAVA_HOME=/home/pbouda/.sdkman/candidates/java/25.0.1-amzn   # default `java` on PATH is 26
MVN=/home/pbouda/.sdkman/candidates/maven/current/bin/mvn
$MVN -q compile                                    # whole reactor (runs the frontend build too)
$MVN -q -pl jeffrey-hub/core-hub -am test          # one module — -am is required, siblings are not in ~/.m2

cd jeffrey-microscope/pages-microscope
npm run dev | build | test | typecheck | format    # build = vue-tsc + vite; `npm run lint` is currently broken
npm run proto:generate                             # after editing profiles/flamegraph/src/main/proto/flamegraph.proto

./run-microscope.sh [--clean]                      # Microscope under async-profiler, data in ~/.jeffrey-microscope
./run-hub-stub.sh                                  # local hub stub (needs hub-api installed in ~/.m2)
```

Verification agents: `java-compiler`, `frontend-builder`, `test-runner`, `design-token-compliance`, `api-contract-reviewer`, `security-reviewer` (MCP, uploads, SQL, gRPC boundaries), `docs-sync-checker`.

### Inspecting DuckDB files
- Per-profile DB `~/.jeffrey-microscope/profiles/<profile-id>/profile-data.db` (`events`, `threads`, `event_types`, …); core DB `~/.jeffrey-microscope/jeffrey-data.db`.
- A running app holds an exclusive lock: **copy the file first** (plus `.wal` if present) and open the copy read-only. No `duckdb` CLI is installed — `python3 -m venv /tmp/ddbvenv && /tmp/ddbvenv/bin/pip install duckdb`, then `duckdb.connect(path, read_only=True)`.
- Schema is `CREATE TABLE` in each `V001__init.sql` (`microscope-core-sql-persistence`, `hub-sql-persistence`, `profile-sql-persistence` under `src/main/resources/db/migration/`); edit it in place — the DB is recreated on every startup. JFR event fields reference: https://sap.github.io/jfrevents/

## Rules that apply everywhere

- **Braces on every control-flow body**, Java and TypeScript, even one statement. Non-negotiable.
- **Design over micro-optimization.** Sealed hierarchies, small records, polymorphism over switch ladders, composition over inheritance — even at the cost of more files. Never trade clarity for a hot-path trick unless the user asks; when you see such a trade-off, state both options in a sentence each and let the user choose. Unsure whether "cleaner" or "faster" is wanted → ask.
- **Spring:** constructor injection only; no `@Component`/`@Service`/`@Repository`/`@Controller`/`@Autowired`. Only `@RestController` (MVC controllers) and `@ControllerAdvice` (`JeffreyExceptionHandler`) are allowed; everything else is an explicit `@Bean`.
- **Time:** inject `java.time.Clock`, never `Instant.now()`; elapsed time via `Measuring`. Frontend timestamps are UTC epoch millis, formatted only by `FormattingService`.
- **Literals:** anything matched, compared, put in SQL or used as config is a named `private static final`; set membership is `Set.of(...).contains`, not an `equals` ladder.
- **Annotations** on classes/fields/methods go on their own line; always `import`, never an inline FQCN; Apache-2.0 header (2026) on every Java file; SLF4J `"what happened: k1={} k2={}"` without commas.
- **Frontend shared-first:** check `@shared`, then `@hubs`, and the design tokens / `shared-components.css` before writing any markup; no hex colors, literal shadows or radii; `DataTable`, `GenericModal`, `Badge`, `PageHeader`/`MainCardHeader`, three-state (`LoadingState` → `ErrorState` → content, `EmptyState`) are mandatory. Generic components go to `shared/ui/common`.
- **Records** for DTOs and for any 3+ parameters or callbacks that travel together; validate in compact constructors with standard exceptions; domain code never depends on Spring/gRPC types — map at the boundary.

## Architecture invariants (never)

- **Hub and Microscope share no domain type.** Each maps `shared/hub-api` protos onto its own model module; `ModuleBoundaryTest` on both sides fails on a cross-import. `shared/common` gets no domain record.
- **MCP belongs to Microscope only** (`POST /api/mcp`; `/api/internal/mcp` is the legacy alias). Never add an MCP endpoint or a JFR reader/log parser to the hub — Microscope pulls chunks and reads them itself.
- **Jeffrey never calls a model provider.** The only AI integration is the MCP server an outside agent calls into; a feature that would put a model inside Jeffrey is a skill or a tool instead.
- **A recording's files are never joined**; a download is an unbroken run of chunks; the hub serves one file per call and reports only `is_recording`; compression happens in the compression job, never on a read path.
- **Protos carry no `reserved`**: hub and Microscope ship together, so removed numbers are reused and survivors renumbered.
- **Artifacts (logs, crash files, dumps) are handed to the agent as a path**, not parsed server-side and not catalogued.
- **The IntelliJ plugin never renders profile data** beyond the recording panel's four figures + findings; everything else links to Microscope.
- Counts and sets repeated in prose are pinned by tests (`McpToolsetAssemblerTest`: 111 tools and the nine writers; `ProfileRouteManifestTest`); update the prose, never loosen the test.

## Testing

JUnit 5 with `@Nested`, Mockito, `@DuckDBTest` from `shared/test`, `Clock.fixed`, Awaitility for anything async, an in-process test per gRPC service (`FileDownloadGrpcServiceTest` is the pattern). Frontend: Vitest.

## Git

- Never commit, tag or push unless the user explicitly asks in that message; a clean build or green tests is not a trigger. Authorization is per change-set — the next request needs a fresh one.
- No `Co-Authored-By: Claude` or any AI trailer, including in the commit a squash-merge produces (GitHub adds one when the squashed commits have another author — pass an explicit message and check the merged commit).

## Documentation

`jeffrey-pages/` documents every user-visible feature; the `docs-sync` rule maps modules to pages and lists the two registrations a new page needs. Update docs with the code change.

## License

Apache-2.0 — header text in `LICENSE_HEADER`.
