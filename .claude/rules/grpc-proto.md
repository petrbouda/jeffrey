---
paths:
  - "**/*.proto"
  - "**/*GrpcService.java"
  - "**/*GrpcServiceTest.java"
  - "jeffrey-microscope/grpc-client/**"
  - "jeffrey-microscope/remote-client/**"
---

## gRPC and Proto Rules

### Proto files
- `proto3`, package `jeffrey.hub.api.v1`, `option java_package = "cafe.jeffrey.hub.api.v1"`, `option java_multiple_files = true`.
- Location: `shared/hub-api/src/main/proto/jeffrey/hub/api/v1/` — `workspace_service`, `project_service`, `instance_service`, `file_download_service`, `repository_service`. Protos only; no Java in that module.
- Request/Response pair per RPC; `repeated` for collections; `workspace_id`/`project_id` on every request scoped to a project or below — the hub resolves nothing without them. Microscope carries the pair as `remote-client`'s `ProjectScope`.

### No `reserved` fields — hub and Microscope ship as one release
- A removed field's number is reused and survivors renumbered contiguously. No gaps, no `reserved` lines.
- This is accepted because the two are never deployed at different versions of `shared/hub-api` (a proto3 field that moves defaults silently on an older peer). Should they ever diverge, flip the rule: keep numbers, reserve removals.
- Proto changes are reflected in `jeffrey-pages/.../docs/hub/HubGrpcApiPage.vue`.

### Services (`jeffrey-hub/core-hub/.../grpc/`)
- Map domain exceptions to status at the service boundary with `GrpcExceptions` (`NOT_FOUND`, `INVALID_ARGUMENT`, `INTERNAL`). Domain code never throws a gRPC status.
- `GrpcExceptions.toStatus` belongs to `GrpcUnary` (a not-found `JeffreyClientException` is `NOT_FOUND`); a **streaming** RPC maps for itself (`FileDownloadGrpcService.downloadFile`: a `FileLookup.Refused` → `INVALID_ARGUMENT` with its `reason()`, a `StatusRuntimeException` passes through — an unknown workspace or project from `GrpcLookups` is already `NOT_FOUND` — a not-found `JeffreyClientException` (an unknown session) is `NOT_FOUND`, anything else is `INTERNAL`).

### Clients
- Clients live in `jeffrey-microscope/grpc-client/`, aggregated by the `RemoteClients` record in `jeffrey-microscope/remote-client/` (`DiscoveryClient`, `RepositoryClient`, `FileStreamClient`, `InstancesClient`, `ProjectsClient`). Add a new client to the record and its factory.
- A message more than one client receives is mapped once, in `ClientProtoMappers`: `session(...)` decodes a session, its files and their statuses for `RepositoryClient` and `InstancesClient` alike — never a second copy in a client.
- Blocking stubs for request/response, async stubs for streaming. Wire values (session ids, file names) are validated and reduced to one path element in the receiving record's compact constructor (`FileStreamClient.TransferredFile`).

### Tests
- Every gRPC service has an in-process test (`InProcessServerBuilder`/`InProcessChannelBuilder`, `grpc-inprocess`), covering status codes and end-to-end streaming. Reference: `FileDownloadGrpcServiceTest` (server-streaming errors arrive via `onError` — await an `errorLatch`); unary reference: `RepositoryGrpcServiceTest` (`assertThrows` on the blocking stub).
