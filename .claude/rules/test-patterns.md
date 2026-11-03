---
paths:
  - "**/*Test.java"
  - "**/*IntegrationTest.java"
  - "**/*.spec.ts"
  - "**/*.test.ts"
---

## Test Rules

### Java
- JUnit 5, `@Nested` classes to group by method or scenario; Mockito for collaborators.
- `@DuckDBTest` (from `shared/test`) for database integration tests — each test gets an isolated DuckDB.
- Time via `Clock.fixed(instant, zone)`; never the real clock.
- Async/polling assertions with Awaitility: `await().atMost(5, SECONDS).untilAsserted(...)`; never `Thread.sleep` loops.
- Naming: `{ClassUnderTest}Test` / `{Feature}IntegrationTest`; method names describe the scenario.
- A recording volume in a test is laid down with `VolumeWriter` (`shared/test`); real recordings come from `RecordedSession` beside it — four consecutive 15-minute async-profiler chunks of one real session, with each chunk's CPU-sample, allocation, park and monitor-enter counts pinned (as `jfr summary` reads them), laid out plain (`writeTo`) or as the hub's compression job leaves them (`writeAsTheHubLeavesIt`). Pin what an answer must hold from those figures, never "something came back".
- Microscope booted whole over a local connector: every class extending `LocalVolumeMicroscope` (`core-microscope`) shares one `@SpringBootTest` (real port, real DuckDB, `applicationClock` fixed by `@TestBean`) over a volume holding `RecordedSession` with a GC log and crash file, a session cut short and one to delete, and drives it over HTTP as an agent (`POST /api/mcp`) and the UI (`/api/internal/**`) do through `MicroscopeClient`, which checks every structured answer against its advertised `outputSchema`. `MicroscopeLocalConnectorIntegrationTest` (status, sessions, windows, refusals, UI pages), `McpFollowUpWalkIntegrationTest` (follows every call any answer suggests, from `connector_status` on; the tools it reaches are pinned), `McpToolSweepIntegrationTest` (every tool callable with the profile alone answers or refuses in a sentence; a recording refuses exactly the heap tools but `heap_status`), `McpAnswerFiguresIntegrationTest`, `McpCompareIntegrationTest` and `ConnectorArtifactsIntegrationTest`; `MicroscopeReadOnlyLocalConnectorIntegrationTest` boots a second, read-only one. They are the one place the chain volume → download → parser → DuckDB → answer runs unmocked: add a scenario there rather than another mock of a layer below the tools. A suggestion that leads nowhere or a tool that fails inside on a real profile fails here first. Their volume and home live under `target/local-connector-it/`.
- Tests that pin a count or a set repeated elsewhere in prose (`McpToolsetAssemblerTest`, `ProfileRouteManifestTest`, `RecordingSessionTest` on both sides) exist so that drift is loud — when one fails, update the prose too, don't loosen the test.

### Frontend
- Vitest (`npm run test` in `jeffrey-microscope/pages-microscope`). `profile-routes.json` is a snapshot of `profileChildRoutes.ts`; regenerate rather than hand-edit.
