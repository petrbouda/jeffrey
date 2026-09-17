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
- gRPC: in-process server per service test; see `grpc-proto.md`.
- Naming: `{ClassUnderTest}Test` / `{Feature}IntegrationTest`; method names describe the scenario.
- Tests that pin a count or a set repeated elsewhere in prose (`McpToolsetAssemblerTest`, `ProfileRouteManifestTest`, `RecordingSessionTest` on both sides) exist so that drift is loud — when one fails, update the prose too, don't loosen the test.

### Frontend
- Vitest (`npm run test` in `jeffrey-microscope/pages-microscope`). `profile-routes.json` is a snapshot of `profileChildRoutes.ts`; regenerate rather than hand-edit.
