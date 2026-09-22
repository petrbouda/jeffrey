# gRPC Proto Validator

Validate that gRPC proto definitions, server implementations, and client code stay in sync across modules.

## Instructions

1. **Read all proto files** in `shared/server-api/src/main/proto/jeffrey/api/v1/` and extract:
   - Each `service` name and its `rpc` methods
   - Skip `common.proto` (contains only shared message types)

2. **Check server implementations** in `jeffrey-server/core-server/src/main/java/pbouda/jeffrey/server/core/grpc/`:
   - Each proto `service XxxService` should have a corresponding `XxxGrpcService.java`
   - Each `rpc MethodName(Request) returns (Response)` should have a `@Override` method in the implementation
   - The class should extend `XxxServiceGrpc.XxxServiceImplBase`
   - Verify `@Component` annotation is present

3. **Check client implementations** in `jeffrey-microscope/core-microscope/src/main/java/pbouda/jeffrey/local/core/client/`:
   - Each proto `service XxxService` should have a corresponding `RemoteXxxClient.java`
   - The client should use `XxxServiceGrpc.XxxServiceBlockingStub` (or appropriate stub type)
   - Each `rpc` method should have a corresponding client method that calls the stub

4. **Check RemoteClients record** at `jeffrey-microscope/core-microscope/src/main/java/pbouda/jeffrey/local/core/client/RemoteClients.java`:
   - All `Remote*Client` types should be included as fields in the record

5. **Cross-reference completeness**:
   - Proto services without server implementation = ERROR
   - Proto services without client = ERROR
   - RPC methods without server implementation = ERROR
   - RPC methods without client method = WARNING
   - Client not in RemoteClients record = ERROR

### Known Mappings

These are the expected mappings (for reference, not hardcoded — always verify against actual files):

| Proto Service | Server Implementation | Client |
|---|---|---|
| `WorkspaceService` | `WorkspaceGrpcService` | `RemoteDiscoveryClient` |
| `ProjectService` | `ProjectGrpcService` | `RemoteProjectsClient` |
| `InstanceService` | `InstanceGrpcService` | `RemoteInstancesClient` |
| `RepositoryService` | `RepositoryGrpcService` | `RemoteRepositoryClient` |
| `RecordingDownloadService` | `RecordingDownloadGrpcService` | `RemoteRecordingStreamClient` |
| `ScopedConfigService` | `ScopedConfigGrpcService` | `ScopedConfigClient` |
| `MessagesService` | `MessagesGrpcService` | `RemoteMessagesClient` |

### Report Format

For each finding, report:
- **File**: path and line number
- **Pattern**: which sync check failed
- **Severity**: ERROR (must fix) or WARNING (should fix)
- **Suggestion**: how to fix it

End with a summary:
- Total services checked
- Total RPC methods checked
- Findings by severity

## When to Use

Run this agent when:
- Adding or modifying proto files in `shared/server-api/`
- Adding new gRPC service implementations
- Adding new remote clients
- Before creating a pull request that touches gRPC-related code
