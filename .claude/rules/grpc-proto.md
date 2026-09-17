---
paths:
  - "**/*.proto"
  - "**/*GrpcService.java"
  - "**/*GrpcServiceTest.java"
  - "jeffrey-microscope/grpc-client/**"
  - "jeffrey-microscope/hub-client/**"
---

## gRPC and Proto Rules

### Proto files
- `proto3`, package `jeffrey.hub.api.v1`, `option java_package = "cafe.jeffrey.hub.api.v1"`, `option java_multiple_files = true`.
- Location: `shared/hub-api/src/main/proto/jeffrey/hub/api/v1/` — `workspace_service`, `project_service`, `instance_service`, `file_download_service`, `repository_service`, `profiler_settings_service`. Protos only; no Java in that module.
- Request/Response pair per RPC; `repeated` for collections; `workspace_id`/`project_id` on resource-scoped requests.

### No `reserved` fields — hub and Microscope ship as one release
- A removed field's number is reused and survivors renumbered contiguously. No gaps, no `reserved` lines.
- This is accepted because the two are never deployed at different versions of `shared/hub-api` (a proto3 field that moves defaults silently on an older peer). Should they ever diverge, flip the rule: keep numbers, reserve removals.
- Proto changes are reflected in `jeffrey-pages/.../docs/hub/HubGrpcApiPage.vue`.

### Services (`jeffrey-hub/core-hub/.../grpc/`)
- Map domain exceptions to status at the service boundary with `GrpcExceptions` (`NOT_FOUND`, `INVALID_ARGUMENT`, `INTERNAL`). Domain code never throws a gRPC status.
- `GrpcExceptions.toStatus` belongs to `GrpcUnary`; a **streaming** RPC maps for itself (`FileDownloadGrpcService.downloadFile`: pass `StatusRuntimeException` through, `IllegalArgumentException` → `INVALID_ARGUMENT` with its own message, then `INTERNAL`).

### Clients
- Clients live in `jeffrey-microscope/grpc-client/`, aggregated by the `HubClients` record in `jeffrey-microscope/hub-client/` (`DiscoveryClient`, `RepositoryClient`, `FileStreamClient`, `ProfilerClient`, `InstancesClient`, `ProjectsClient`). Add a new client to the record and its factory.
- Blocking stubs for request/response, async stubs for streaming. Wire values (session ids, file names) are validated and reduced to one path element in the receiving record's compact constructor (`FileStreamClient.TransferredFile`).

### Tests
- Every gRPC service has an in-process test (`InProcessServerBuilder`/`InProcessChannelBuilder`, `grpc-inprocess`), covering status codes and end-to-end streaming. Reference: `FileDownloadGrpcServiceTest` (server-streaming errors arrive via `onError` — await an `errorLatch`); unary reference: `RepositoryGrpcServiceTest` (`assertThrows` on the blocking stub).
