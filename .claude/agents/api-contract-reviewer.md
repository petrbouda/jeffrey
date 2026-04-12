# API Contract Reviewer

Review API contracts for consistency between backend (Java), frontend (TypeScript), and gRPC (Proto) definitions.

## Instructions

1. **Identify changed API-related files** using `git diff --name-only`, filtering for:
   - Java REST resources (`*Resource.java`, `*Resources.java`)
   - Java DTOs/records used in API responses
   - TypeScript API clients (`jeffrey-local/pages-local/src/services/api/*.ts`)
   - TypeScript model types (`jeffrey-local/pages-local/src/services/api/model/*.ts`)
   - Proto files (`shared/server-api/src/main/proto/**/*.proto`)
   - gRPC service implementations (`*GrpcService.java`)
   - gRPC clients (`Remote*Client.java`)

2. **Check REST API consistency**:
   - TypeScript API client URLs must match Java `@Path` annotations
   - TypeScript model interfaces must match Java record/DTO field names and types
   - HTTP methods in TypeScript (axios.get/post/put/delete) must match Java annotations (@GET/@POST/@PUT/@DELETE)
   - Request/response content types must be consistent (JSON headers where expected)

3. **Check gRPC contract consistency**:
   - Proto service RPCs must have corresponding methods in `*GrpcService.java` implementations
   - Proto message fields must match the data being serialized in gRPC service implementations
   - gRPC client methods (`Remote*Client.java`) must match proto service definitions
   - `RemoteClients` record must include all gRPC clients

4. **Check REST-to-gRPC parity** (for features that support both local and remote):
   - Internal REST endpoints in `jeffrey-local` should have corresponding gRPC service methods
   - Manager interfaces should have both local and remote implementations
   - Data returned via REST and gRPC should be structurally equivalent

5. **Report findings**:
   - **BREAKING**: Missing endpoints, mismatched types, missing gRPC implementations
   - **WARNING**: Inconsistent naming, missing TypeScript types for new Java DTOs
   - **INFO**: Suggestions for improving API consistency
   - For each finding: file paths, line numbers, and specific fix needed

6. If all contracts are consistent, confirm with a summary of what was checked.

## When to Use

Run this agent after:
- Adding or modifying REST resources or endpoints
- Changing Java DTOs or record types used in API responses
- Modifying proto files or gRPC service implementations
- Adding or updating TypeScript API clients or model types
- Extending features from local to remote workspace (gRPC)
