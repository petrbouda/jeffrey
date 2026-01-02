# Extend Feature to Remote Workspace

Extend a feature that currently only works with LIVE workspaces to also work with REMOTE workspaces.

## Usage

```
/extend-feature-to-remote-workspace
```

## Background

Jeffrey has two types of workspaces:
- **LIVE workspaces**: Local workspaces where data is stored directly in the local database
- **REMOTE workspaces**: Workspaces that connect to a remote Jeffrey instance and proxy operations to it

When extending a feature to support REMOTE workspaces, you need to:
1. Expose the feature via **public REST endpoints** on the remote server
2. Add **client methods** to call those endpoints from the local Jeffrey
3. Create **managers** that delegate to either local or remote implementations
4. Update the **frontend** to use the unified API

## Architecture Overview

```
Local Jeffrey Instance
├── Internal API (frontend calls this)
│   └── ProjectResource
│       └── YourFeatureResource (uses ProjectManager.yourFeatureManager())
│
├── ProjectManager interface
│   ├── CommonProjectManager (LIVE) → LiveYourFeatureManager
│   └── RemoteProjectManager (REMOTE) → RemoteYourFeatureManager
│
└── RemoteWorkspaceClient → calls remote public API

Remote Jeffrey Instance
└── Public API
    └── ProjectProfilerSettingsPublicResource (exposes feature to remote clients)
```

## Workflow

### Step 1: Create Response/Request DTOs

Create DTOs in `service/platform-management/src/main/java/pbouda/jeffrey/platform/resources/response/` and `request/`:

```java
// response/YourFeatureResponse.java
public record YourFeatureResponse(
        String field1,
        String field2) {

    public static YourFeatureResponse from(YourDomainModel model) {
        return new YourFeatureResponse(model.field1(), model.field2());
    }
}

// request/YourFeatureRequest.java
public record YourFeatureRequest(
        String field1,
        String field2) {
}
```

### Step 2: Create Public REST Resource (Remote Server Side)

Create a public resource in `service/platform-management/src/main/java/pbouda/jeffrey/platform/resources/pub/`:

```java
// YourFeaturePublicResource.java
public class YourFeaturePublicResource {

    private final YourRepository repository;
    private final String workspaceId;
    private final String projectId;

    @GET
    public List<YourFeatureResponse> fetch() {
        // Fetch and return data
    }

    @POST
    public Response upsert(YourFeatureRequest request) {
        // Process and save
        return Response.ok().build();
    }

    @DELETE
    public Response delete() {
        // Delete
        return Response.noContent().build();
    }
}
```

### Step 3: Wire Public Resource into Hierarchy

Update resources in the public resource chain to pass required dependencies:

1. **RootPublicResource.java** - Inject your repository/dependencies
2. **WorkspacesPublicResource.java** - Pass dependencies through constructor
3. **WorkspacePublicResource.java** - Pass dependencies through constructor
4. **WorkspaceProjectsPublicResource.java** - Pass dependencies through constructor
5. **WorkspaceProjectPublicResource.java** - Create and expose your resource

```java
// In WorkspaceProjectPublicResource.java
@Path("/your-feature")
public YourFeaturePublicResource yourFeatureResource() {
    ProjectInfo projectInfo = projectManager.info();
    return new YourFeaturePublicResource(repository, projectInfo.workspaceId(), projectInfo.id());
}
```

### Step 4: Add Client Interface Methods

Add methods to `RemoteWorkspaceClient.java`:

```java
List<YourFeatureResponse> fetchYourFeature(String workspaceId, String projectId);
void upsertYourFeature(String workspaceId, String projectId, YourFeatureRequest request);
void deleteYourFeature(String workspaceId, String projectId);
```

### Step 5: Implement Client Methods

Add implementation to `RemoteWorkspaceClientImpl.java`:

```java
private static final String API_YOUR_FEATURE = API_WORKSPACES_PROJECTS + "/{projectId}/your-feature";

private static final ParameterizedTypeReference<List<YourFeatureResponse>> YOUR_FEATURE_LIST_TYPE =
        new ParameterizedTypeReference<>() {};

@Override
public List<YourFeatureResponse> fetchYourFeature(String workspaceId, String projectId) {
    ResponseEntity<List<YourFeatureResponse>> response = invokeGet(uri, () -> {
        return restClient.get()
                .uri(API_YOUR_FEATURE, workspaceId, projectId)
                .retrieve()
                .toEntity(YOUR_FEATURE_LIST_TYPE);
    });
    return response.getBody();
}

@Override
public void upsertYourFeature(String workspaceId, String projectId, YourFeatureRequest request) {
    invokePost(uri, () -> {
        return restClient.post()
                .uri(API_YOUR_FEATURE, workspaceId, projectId)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    });
}

@Override
public void deleteYourFeature(String workspaceId, String projectId) {
    invokeDelete(uri, () -> {
        return restClient.delete()
                .uri(API_YOUR_FEATURE, workspaceId, projectId)
                .retrieve()
                .toBodilessEntity();
    });
}
```

### Step 6: Create Manager Interface

Create a manager interface in `service/platform-management/src/main/java/pbouda/jeffrey/platform/manager/`:

```java
// YourFeatureManager.java
public interface YourFeatureManager {
    List<YourDomainModel> fetchAll();
    void upsert(String value);
    void delete();
}
```

### Step 7: Create Live Manager Implementation

```java
// LiveYourFeatureManager.java
public class LiveYourFeatureManager implements YourFeatureManager {

    private final YourRepository repository;
    private final String workspaceId;
    private final String projectId;

    @Override
    public List<YourDomainModel> fetchAll() {
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
// RemoteYourFeatureManager.java
public class RemoteYourFeatureManager implements YourFeatureManager {

    private final RemoteWorkspaceClient remoteWorkspaceClient;
    private final WorkspaceInfo workspaceInfo;
    private final String projectId;

    @Override
    public List<YourDomainModel> fetchAll() {
        List<YourFeatureResponse> responses = remoteWorkspaceClient.fetchYourFeature(
                workspaceInfo.originId(), projectId);
        return responses.stream()
                .map(r -> new YourDomainModel(r.field1(), r.field2()))
                .toList();
    }

    @Override
    public void upsert(String value) {
        remoteWorkspaceClient.upsertYourFeature(workspaceInfo.originId(), projectId, new YourFeatureRequest(value));
    }

    @Override
    public void delete() {
        remoteWorkspaceClient.deleteYourFeature(workspaceInfo.originId(), projectId);
    }
}
```

### Step 9: Add to ProjectManager Interface

Add the manager method to `ProjectManager.java`:

```java
YourFeatureManager yourFeatureManager();
```

### Step 10: Implement in CommonProjectManager

```java
@Override
public YourFeatureManager yourFeatureManager() {
    return new LiveYourFeatureManager(
            repositories.newYourRepository(),
            projectInfo.workspaceId(),
            projectInfo.id());
}
```

### Step 11: Implement in RemoteProjectManager

```java
@Override
public YourFeatureManager yourFeatureManager() {
    return new RemoteYourFeatureManager(
            remoteWorkspaceClient,
            workspaceInfo,
            detailedProjectInfo.projectInfo().originId());
}
```

### Step 12: Create Internal REST Resource

Create the internal resource in `service/platform-management/src/main/java/pbouda/jeffrey/platform/resources/project/`:

```java
// YourFeatureResource.java
public class YourFeatureResource {

    private final YourFeatureManager manager;

    @GET
    public List<YourFeatureResponse> fetch() {
        return manager.fetchAll().stream()
                .map(YourFeatureResponse::from)
                .toList();
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

Add to `ProjectResource.java`:

```java
@Path("/your-feature")
public YourFeatureResource yourFeatureResource() {
    return new YourFeatureResource(projectManager.yourFeatureManager());
}
```

### Step 14: Update Frontend API Client

Add project-level methods to your TypeScript client:

```typescript
// YourFeatureClient.ts
static projectBaseUrl(workspaceId: string, projectId: string): string {
    return `${GlobalVars.internalUrl}/workspaces/${workspaceId}/projects/${projectId}/your-feature`;
}

static fetchProjectFeature(workspaceId: string, projectId: string): Promise<YourFeature[]> {
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
| Response DTOs | `service/platform-management/src/main/java/pbouda/jeffrey/platform/resources/response/` |
| Request DTOs | `service/platform-management/src/main/java/pbouda/jeffrey/platform/resources/request/` |
| Public Resources | `service/platform-management/src/main/java/pbouda/jeffrey/platform/resources/pub/` |
| Internal Resources | `service/platform-management/src/main/java/pbouda/jeffrey/platform/resources/project/` |
| Managers | `service/platform-management/src/main/java/pbouda/jeffrey/platform/manager/` |
| RemoteWorkspaceClient | `service/platform-management/src/main/java/pbouda/jeffrey/platform/manager/workspace/remote/` |
| ProjectManager | `service/platform-management/src/main/java/pbouda/jeffrey/platform/manager/project/` |
| Frontend Clients | `pages/src/services/api/` |

## Verification

After implementation:
1. Run `mvn clean compile` to verify backend compiles
2. Run `npm run build` in `pages/` to verify frontend compiles
3. Test with a LIVE workspace - should work as before
4. Test with a REMOTE workspace - should now work via the remote API

## Key Principles

1. **Unified API**: Frontend always calls internal API; backend handles delegation
2. **Manager Pattern**: Use interface + local/remote implementations for delegation
3. **Public vs Internal**: Public resources are exposed at `/api/public/`, internal at `/api/internal/`
4. **originId**: For remote workspaces, use `originId` (the remote's ID) when calling remote APIs
5. **Dependency Chain**: Pass dependencies through the resource hierarchy to reach your feature
