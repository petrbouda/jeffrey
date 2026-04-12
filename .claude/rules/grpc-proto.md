---
paths:
  - "**/*.proto"
  - "**/*GrpcService.java"
  - "**/client/Remote*.java"
---

## gRPC and Proto Rules

### Proto File Conventions
- Syntax: `proto3`
- Package: `jeffrey.api.v1`
- Java package: `pbouda.jeffrey.api.v1` (via `option java_package`)
- Always set `option java_multiple_files = true`
- Location: `shared/server-api/src/main/proto/jeffrey/api/v1/`

### Message Design
- Use Request/Response pairs for each RPC (e.g., `GetWorkspaceRequest`, `GetWorkspaceResponse`)
- Include `workspace_id` and `project_id` in requests that operate on specific resources
- Use `repeated` for collections, not wrapper messages
- Field numbering: sequential, never reuse numbers from removed fields

### gRPC Service Implementation
- Implementations go in `jeffrey-server/core-server/.../grpc/`
- Must handle errors with proper gRPC status codes: `NOT_FOUND`, `INVALID_ARGUMENT`, `INTERNAL`
- Use `GrpcExceptions` utility for common status patterns
- Map domain exceptions to gRPC status at the service boundary, not in domain code

### gRPC Clients
- Clients go in `jeffrey-local/core-local/.../client/`
- Use blocking stubs for request-response, async stubs for streaming
- Add new clients to the `RemoteClients` record
- Update the factory method that creates `RemoteClients`

### Testing
- Every gRPC service must have an in-process integration test
- Use `InProcessServerBuilder` / `InProcessChannelBuilder`
- Test validation errors (status codes) and end-to-end streaming
- Reference pattern: `EventStreamingGrpcServiceTest`
