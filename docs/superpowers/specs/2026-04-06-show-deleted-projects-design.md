# Show Deleted Projects with Restore

## Summary

Add the ability to view soft-deleted projects in the project listing UI, behind an opt-in toggle. Deleted projects appear greyed-out with a "Deleted" badge and a restore button. This helps debug why certain projects aren't being processed on the client side.

## Context

Projects are soft-deleted via a `deleted_at TIMESTAMPTZ` column in the server database (commit 78d5f0f2). Every SQL query filters with `AND deleted_at IS NULL`, making deleted projects invisible across the entire stack. Users have no way to see which projects were deleted or restore them.

## Design

### 1. gRPC Proto (`shared/server-api/src/main/proto/jeffrey/server/api/v1/project_service.proto`)

Add `include_deleted` flag to request, `deleted_at` field to response, and a new `RestoreProject` RPC:

```protobuf
message ListProjectsRequest {
  string workspace_id = 1;
  bool include_deleted = 2;
}

message ProjectInfo {
  // ... existing fields 1-11 ...
  optional int64 deleted_at = 12;
}

rpc RestoreProject(RestoreProjectRequest) returns (RestoreProjectResponse);

message RestoreProjectRequest {
  string workspace_id = 1;
  string project_id = 2;
}

message RestoreProjectResponse {}
```

### 2. Server Database Layer

**`JdbcProjectsRepository`** — new query and method:

```sql
-- SELECT_PROJECTS_BY_WORKSPACE_INCLUDING_DELETED
SELECT * FROM projects p
JOIN workspaces w ON p.workspace_id = w.workspace_id
WHERE p.workspace_id = :workspace_id
```

New method: `findAllProjectsIncludingDeleted(String workspaceId)` on `ProjectsRepository` interface and implementation.

**`JdbcProjectRepository`** — new restore SQL and method:

```sql
-- RESTORE_PROJECT
UPDATE projects SET deleted_at = NULL WHERE project_id = :project_id
```

New method: `restore()` on `ProjectRepository` interface and implementation.

**`ProjectInfo` record** (`shared/common`) — add `Instant deletedAt` field.

**`ServerMappers.projectInfoMapper()`** — map the `deleted_at` column to the new field.

### 3. Server Manager Layer

**`ProjectsManager` interface** — add `findAllIncludingDeleted()` returning `List<ProjectManager>`.

**`LiveProjectsManager`** — implement using `projectsRepository.findAllProjectsIncludingDeleted()`.

**`ProjectManager` interface** — add `restore()` method.

**Server `ProjectManager` implementation** — implement `restore()` via `projectRepository.restore()`.

### 4. Server gRPC Service (`ProjectGrpcService`)

**`listProjects`**: Check `request.getIncludeDeleted()` flag. When true, call `findAllIncludingDeleted()` instead of `findAll()`. Map `deletedAt` to proto's `deleted_at` field.

**New `restoreProject`**: Find the workspace, find the project (must work for deleted projects — the `project()` lookup needs a variant that includes deleted, or use `findAllIncludingDeleted` and filter). Call `restore()`. Return empty response.

**`toProto`**: Map `deletedAt` from `ProjectInfo` to proto `deleted_at` field when non-null.

**`JdbcProjectRepository.find()`**: Remove the `AND p.deleted_at IS NULL` filter from `SELECT_SINGLE_PROJECT`. Direct ID lookups for management operations (restore, block, unblock) should find the project regardless of deletion status. The `deleted_at` filtering belongs on list queries, not single-entity lookups. This means the existing `findProject` helper in `ProjectGrpcService` works for restore without changes.

### 5. Local gRPC Client (`RemoteDiscoveryClient`)

**`allProjects`** method: Add `boolean includeDeleted` parameter. Set `include_deleted` on the gRPC `ListProjectsRequest`.

Map `deleted_at` from proto:
```java
proto.hasDeletedAt() ? Instant.ofEpochMilli(proto.getDeletedAt()) : null
```

**`RemoteProjectResponse`**: Add `Instant deletedAt` field.

**New `restoreProject`** method: Call the `RestoreProject` gRPC RPC.

### 6. Local Manager + REST Layer

**`ProjectsManager` interface** (local): Add `findAllIncludingDeleted()`.

**Remote `ProjectsManager` implementation**: Implement using `RemoteDiscoveryClient.allProjects(workspaceId, true)`.

**`ProjectManager` interface** (local): Add `restore()`.

**`RemoteProjectManager`**: Implement `restore()` via gRPC client.

**`WorkspaceProjectsResource.projects()`**: Accept `@QueryParam("includeDeleted") boolean includeDeleted`. Call `findAllIncludingDeleted()` when true, `findAll()` otherwise.

**`ProjectResponse`**: Add `boolean isDeleted` and `Long deletedAt`.

**`Mappers.toProjectResponse`**: Map `isDeleted` from `deletedAt != null`, map `deletedAt` as epoch millis.

**`DetailedProjectInfo`** (local): Add `boolean isDeleted` field.

### 7. Frontend

**`Project.ts`**: Add `isDeleted: boolean` and `deletedAt: number | null` fields.

**`ProjectsClient.ts`**: Add `includeDeleted` query param to `list()` call:
```typescript
async list(workspaceId: string, includeDeleted: boolean = false): Promise<Project[]> {
  const params = includeDeleted ? '?includeDeleted=true' : '';
  return super.get<Project[]>(`/workspaces/${workspaceId}/projects${params}`);
}
```

**`ProjectCard.vue`**:
- New `border-deleted` CSS class: same pattern as `border-blocked` (greyed-out border, reduced opacity)
- "Deleted" badge in the footer row using existing `Badge` component with `variant="status-blocked"` (grey)
- Card is **not clickable** when deleted — no navigation, cursor changes to default
- Restore icon button (`bi-arrow-counterclockwise`) on the name row, emits `@restore` event

**`ProjectsView.vue`**:
- Always fetch with `includeDeleted=true` so the toggle works instantly without re-fetching
- New `showDeletedProjects` ref (default: `false`)
- New `hasDeletedProjects` computed: `projects.value.some(p => p.isDeleted)`
- New toggle button next to existing "Show blocked" toggle, icon: `bi-trash`, same styling pattern
- `filterProjects` function: exclude deleted projects when toggle is off (same pattern as blocked filter)
- Handle `@restore` event from `ProjectCard`: call restore API, refresh projects, show toast

## Non-Goals

- Permanent deletion of soft-deleted projects (can be added later)
- Showing deletion timestamp in the UI beyond the badge (keep it simple)
- Auto-purging old deleted projects
