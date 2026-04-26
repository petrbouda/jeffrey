# Extend Feature to Remote Workspace

Extend a feature that currently only works with LIVE workspaces to also work with REMOTE workspaces.

## Usage

```
/extend-feature-to-remote-workspace
```

## Background

Jeffrey has two types of workspaces:
- **LIVE workspaces**: Local workspaces where data is stored directly in the local database
- **REMOTE workspaces**: Workspaces that connect to a remote Jeffrey server and proxy operations to it via gRPC

When extending a feature to support REMOTE workspaces, you need to:
1. Define the gRPC service contract in a **proto file** on the shared server-api module
2. Implement the gRPC service on **jeffrey-server**
3. Create a **gRPC client** on jeffrey-local to call the remote server
4. Create **managers** that delegate to either local or remote implementations
5. The **frontend** calls the same internal REST API regardless of workspace type

## Architecture Overview

```
jeffrey-local (Local Instance)
├── Internal REST API (frontend calls this)
│   └── ProjectResource → YourFeatureResource
├── ProjectManager interface
│   ├── ProjectManager (local) → direct implementation
│   └── RemoteProjectManager (remote) → delegates to gRPC client
└── gRPC Clients (RemoteClients)
    └── RemoteYourFeatureClient → calls jeffrey-server gRPC

jeffrey-server (Remote Server Instance)
└── gRPC Services
    └── YourFeatureGrpcService (implements proto-generated service)
```

## Workflow

### Step 1: Define gRPC Service in Proto File

Create or extend a proto file in `shared/server-api/src/main/proto/jeffrey/api/v1/`:

```protobuf
// your_feature_service.proto
syntax = "proto3";

package jeffrey.api.v1;

option java_package = "cafe.jeffrey.api.v1";
option java_multiple_files = true;

service YourFeatureService {
  rpc GetYourFeature(GetYourFeatureRequest) returns (GetYourFeatureResponse);
  rpc UpsertYourFeature(UpsertYourFeatureRequest) returns (UpsertYourFeatureResponse);
  rpc DeleteYourFeature(DeleteYourFeatureRequest) returns (DeleteYourFeatureResponse);
}

message GetYourFeatureRequest {
  string workspace_id = 1;
  string project_id = 2;
}

message GetYourFeatureResponse {
  string field1 = 1;
  string field2 = 2;
}

message UpsertYourFeatureRequest {
  string workspace_id = 1;
  string project_id = 2;
  string field1 = 3;
}

message UpsertYourFeatureResponse {}

message DeleteYourFeatureRequest {
  string workspace_id = 1;
  string project_id = 2;
}

message DeleteYourFeatureResponse {}
```

### Step 2: Generate gRPC Stubs

Run `mvn compile` on `shared/server-api` to generate the Java gRPC stubs from the proto file. This generates the `*Grpc` stub classes and message classes in the `cafe.jeffrey.api.v1` package.

### Step 3: Implement gRPC Service on jeffrey-server

Create a gRPC service in `jeffrey-server/core-server/src/main/java/pbouda/jeffrey/server/core/grpc/`:

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

package cafe.jeffrey.server.core.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import cafe.jeffrey.api.v1.*;
import cafe.jeffrey.server.core.manager.project.ProjectManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspacesManager;

@Component
public class YourFeatureGrpcService extends YourFeatureServiceGrpc.YourFeatureServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(YourFeatureGrpcService.class);

    private final WorkspacesManager workspacesManager;

    public YourFeatureGrpcService(WorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    @Override
    public void getYourFeature(GetYourFeatureRequest request, StreamObserver<GetYourFeatureResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());
            // Fetch data using project manager
            var data = project.yourFeatureManager().fetch();

            LOG.debug("Fetched your feature via gRPC: projectId={}", request.getProjectId());

            GetYourFeatureResponse response = GetYourFeatureResponse.newBuilder()
                    .setField1(data.field1())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to get your feature: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void upsertYourFeature(UpsertYourFeatureRequest request, StreamObserver<UpsertYourFeatureResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());
            project.yourFeatureManager().upsert(request.getField1());

            LOG.debug("Upserted your feature via gRPC: projectId={}", request.getProjectId());

            responseObserver.onNext(UpsertYourFeatureResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to upsert your feature: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteYourFeature(DeleteYourFeatureRequest request, StreamObserver<DeleteYourFeatureResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());
            project.yourFeatureManager().delete();

            LOG.debug("Deleted your feature via gRPC: projectId={}", request.getProjectId());

            responseObserver.onNext(DeleteYourFeatureResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to delete your feature: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private ProjectManager findProject(String workspaceId, String projectId) {
        WorkspaceManager workspace = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Workspace not found: " + workspaceId)
                        .asRuntimeException());
        return workspace.projectsManager().project(projectId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Project not found: " + projectId)
                        .asRuntimeException());
    }
}
```

### Step 4: Create gRPC Client on jeffrey-local

Create a gRPC client in `jeffrey-local/core-local/src/main/java/pbouda/jeffrey/local/core/client/`:

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

package cafe.jeffrey.local.core.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.api.v1.*;

public class RemoteYourFeatureClient {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteYourFeatureClient.class);

    private final YourFeatureServiceGrpc.YourFeatureServiceBlockingStub stub;

    public RemoteYourFeatureClient(GrpcServerConnection connection) {
        this.stub = YourFeatureServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public YourDomainModel fetchYourFeature(String workspaceId, String projectId) {
        GetYourFeatureResponse response = stub.getYourFeature(
                GetYourFeatureRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .setProjectId(projectId)
                        .build());

        LOG.debug("Fetched your feature via gRPC: workspaceId={} projectId={}", workspaceId, projectId);

        return new YourDomainModel(response.getField1(), response.getField2());
    }

    public void upsertYourFeature(String workspaceId, String projectId, String field1) {
        stub.upsertYourFeature(
                UpsertYourFeatureRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .setProjectId(projectId)
                        .setField1(field1)
                        .build());

        LOG.debug("Upserted your feature via gRPC: workspaceId={} projectId={}", workspaceId, projectId);
    }

    public void deleteYourFeature(String workspaceId, String projectId) {
        stub.deleteYourFeature(
                DeleteYourFeatureRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .setProjectId(projectId)
                        .build());

        LOG.debug("Deleted your feature via gRPC: workspaceId={} projectId={}", workspaceId, projectId);
    }
}
```

### Step 5: Wire Client into RemoteClients Record

Add the new client to the `RemoteClients` record in `jeffrey-local/core-local/src/main/java/pbouda/jeffrey/local/core/client/RemoteClients.java`:

```java
public record RemoteClients(
        RemoteDiscoveryClient discovery,
        RemoteRepositoryClient repository,
        RemoteRecordingStreamClient recordings,
        RemoteProfilerClient profiler,
        RemoteMessagesClient messages,
        RemoteInstancesClient instances,
        RemoteProjectsClient projects,
        RemoteYourFeatureClient yourFeature      // ADD THIS
) {

    @FunctionalInterface
    public interface Factory extends Function<URI, RemoteClients> {
    }
}
```

Also update the factory method that creates `RemoteClients` to instantiate `RemoteYourFeatureClient` from the `GrpcServerConnection`.

### Step 6: Create Manager Interface

Create a manager interface in `jeffrey-local/core-local/src/main/java/pbouda/jeffrey/local/core/manager/`:

```java
// YourFeatureManager.java
public interface YourFeatureManager {
    YourDomainModel fetch();
    void upsert(String value);
    void delete();
}
```

### Step 7: Create Local Manager Implementation

```java
// LocalYourFeatureManager.java in jeffrey-local/core-local/.../manager/
public class LocalYourFeatureManager implements YourFeatureManager {

    private final YourRepository repository;
    private final String workspaceId;
    private final String projectId;

    @Override
    public YourDomainModel fetch() {
        // Use local repository
    }

    @Override
    public void upsert(String value) {
        // Use local repository
    }

    @Override
    public void delete() {
        // Use local repository
    }
}
```

### Step 8: Create Remote Manager Implementation

```java
// RemoteYourFeatureManager.java in jeffrey-local/core-local/.../manager/
public class RemoteYourFeatureManager implements YourFeatureManager {

    private final RemoteYourFeatureClient remoteClient;
    private final RemoteWorkspaceInfo workspaceInfo;
    private final String projectId;

    @Override
    public YourDomainModel fetch() {
        return remoteClient.fetchYourFeature(workspaceInfo.id(), projectId);
    }

    @Override
    public void upsert(String value) {
        remoteClient.upsertYourFeature(workspaceInfo.id(), projectId, value);
    }

    @Override
    public void delete() {
        remoteClient.deleteYourFeature(workspaceInfo.id(), projectId);
    }
}
```

### Step 9: Add to ProjectManager Interface

Add the manager method to `jeffrey-local/core-local/src/main/java/pbouda/jeffrey/local/core/manager/project/ProjectManager.java`:

```java
YourFeatureManager yourFeatureManager();
```

### Step 10: Implement in Local ProjectManager

In the local `ProjectManager` implementation:

```java
@Override
public YourFeatureManager yourFeatureManager() {
    return new LocalYourFeatureManager(
            repository,
            projectInfo.workspaceId(),
            projectInfo.id());
}
```

### Step 11: Implement in RemoteProjectManager

In `jeffrey-local/core-local/src/main/java/pbouda/jeffrey/local/core/manager/project/RemoteProjectManager.java`:

```java
@Override
public YourFeatureManager yourFeatureManager() {
    return new RemoteYourFeatureManager(
            remoteClients.yourFeature(),
            workspaceInfo,
            detailedProjectInfo.projectInfo().id());
}
```

### Step 12: Create Internal REST Resource

Create the internal resource in `jeffrey-local/core-local/src/main/java/pbouda/jeffrey/local/core/resources/project/`:

```java
// YourFeatureResource.java
public class YourFeatureResource {

    private final YourFeatureManager manager;

    @GET
    public YourFeatureResponse fetch() {
        YourDomainModel data = manager.fetch();
        return YourFeatureResponse.from(data);
    }

    @POST
    public Response upsert(YourFeatureRequest request) {
        manager.upsert(request.value());
        return Response.ok().build();
    }

    @DELETE
    public Response delete() {
        manager.delete();
        return Response.noContent().build();
    }
}
```

### Step 13: Wire Internal Resource

Add to `ProjectResource.java` in `jeffrey-local/core-local/src/main/java/pbouda/jeffrey/local/core/resources/project/`:

```java
@Path("/your-feature")
public YourFeatureResource yourFeatureResource() {
    return new YourFeatureResource(projectManager.yourFeatureManager());
}
```

### Step 14: Update Frontend API Client

Add project-level methods to your TypeScript client in `jeffrey-local/pages-local/src/services/api/`:

```typescript
// YourFeatureClient.ts
static projectBaseUrl(workspaceId: string, projectId: string): string {
    return `${GlobalVars.internalUrl}/workspaces/${workspaceId}/projects/${projectId}/your-feature`;
}

static fetchProjectFeature(workspaceId: string, projectId: string): Promise<YourFeature> {
    return axios.get(this.projectBaseUrl(workspaceId, projectId), HttpUtils.JSON_ACCEPT_HEADER)
        .then(HttpUtils.RETURN_DATA);
}

static upsertProjectFeature(workspaceId: string, projectId: string, value: string): Promise<void> {
    const content = { value };
    return axios.post(this.projectBaseUrl(workspaceId, projectId), content, HttpUtils.JSON_HEADERS)
        .then(HttpUtils.RETURN_DATA);
}

static deleteProjectFeature(workspaceId: string, projectId: string): Promise<void> {
    return axios.delete(this.projectBaseUrl(workspaceId, projectId))
        .then(HttpUtils.RETURN_DATA);
}
```

### Step 15: Update Vue Component

Remove remote workspace checks and use the project-level API:

```typescript
// Before
if (isRemoteWorkspace.value) {
  // Show "not supported" message
  return;
}
const data = await YourFeatureClient.fetchGlobal();

// After (works for both LIVE and REMOTE)
const data = await YourFeatureClient.fetchProjectFeature(workspaceId.value, projectId.value);
```

## File Locations Reference

| Component | Path |
|-----------|------|
| Proto files | `shared/server-api/src/main/proto/jeffrey/api/v1/` |
| gRPC service implementations | `jeffrey-server/core-server/src/main/java/pbouda/jeffrey/server/core/grpc/` |
| gRPC clients | `jeffrey-local/core-local/src/main/java/pbouda/jeffrey/local/core/client/` |
| RemoteClients record | `jeffrey-local/core-local/src/main/java/pbouda/jeffrey/local/core/client/RemoteClients.java` |
| GrpcServerConnection | `jeffrey-local/core-local/src/main/java/pbouda/jeffrey/local/core/client/GrpcServerConnection.java` |
| Manager interfaces | `jeffrey-local/core-local/src/main/java/pbouda/jeffrey/local/core/manager/` |
| ProjectManager interface | `jeffrey-local/core-local/src/main/java/pbouda/jeffrey/local/core/manager/project/ProjectManager.java` |
| RemoteProjectManager | `jeffrey-local/core-local/src/main/java/pbouda/jeffrey/local/core/manager/project/RemoteProjectManager.java` |
| Internal REST resources | `jeffrey-local/core-local/src/main/java/pbouda/jeffrey/local/core/resources/project/` |
| Frontend API clients | `jeffrey-local/pages-local/src/services/api/` |

## Verification

After implementation:
1. Run `mvn clean compile` to verify proto generation and backend compiles
2. Run `cd jeffrey-local/pages-local && npm run build` to verify frontend compiles
3. Test with a LIVE workspace - should work as before
4. Test with a REMOTE workspace - should now work via gRPC

## Key Principles

1. **Unified API**: Frontend always calls internal REST API; backend handles delegation to local or remote
2. **Manager Pattern**: Use interface + local/remote implementations for delegation
3. **gRPC Contract**: Proto files in `shared/server-api` define the contract between jeffrey-local and jeffrey-server
4. **gRPC Replaces REST Public API**: Remote workspace communication uses gRPC, not REST public endpoints
5. **RemoteClients Record**: All gRPC clients are grouped in the `RemoteClients` record for clean dependency injection
6. **GrpcServerConnection**: Manages the underlying `ManagedChannel` for creating gRPC stubs; supports both plaintext and TLS
