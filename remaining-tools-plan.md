# Implementation Plan: ProfileToolManager + 5 Remaining Tools

## Context

First batch (Collapse Frames, Remove Frames, Trim to Time Range) is implemented but each tool manager is exposed individually on `ProfileManager`. The user wants a unified `ProfileToolManager` (following `ProfileCustomManager` pattern) that aggregates all tool managers. Then implement the 5 remaining tools.

## Phase 0: Refactor — Create ProfileToolManager aggregator

**Pattern:** Exactly like `ProfileCustomManager` — an interface that aggregates multiple managers with `Factory extends Function<ProfileManager, ProfileToolManager>`.

### Create `ProfileToolManager` interface
**File:** `profiles/profile-management/src/.../manager/ProfileToolManager.java`
```java
public interface ProfileToolManager {
    @FunctionalInterface
    interface Factory extends Function<ProfileManager, ProfileToolManager> {}

    // Existing tools
    ProfileToolsManager renameFrames();
    CollapseFramesManager collapseFrames();
    RemoveFramesManager removeFrames();
    TrimTimeRangeManager trimTimeRange();

    // New tools (added later in this plan)
    RenameThreadsManager renameThreads();
    FilterByEventTypeManager filterByEventType();
    FilterByThreadManager filterByThread();
    TrimStacktraceRootsManager trimStacktraceRoots();
    AnonymizeProfileManager anonymize();
}
```

### Create `ProfileToolManagerImpl`
**File:** `profiles/profile-management/src/.../manager/ProfileToolManagerImpl.java`
- Constructor takes `ProfileManager parent` + all individual `Manager.Factory` instances
- Each accessor: `return factory.apply(parent.info())`

### Create `ProfileToolFactoriesConfiguration`
**File:** `profiles/profile-management/src/.../configuration/ProfileToolFactoriesConfiguration.java`
- `@Bean` for each tool factory (move existing ones from `ProfileFactoriesConfiguration`)
- `@Bean ProfileToolManager.Factory profileToolManagerFactory(...)` — aggregates all tool factories

### Update existing files
- **`ProfileManagerFactoryRegistry`**: Replace individual tool factory fields (`tools`, `collapseFrames`, `removeFrames`, `trimTimeRange`) with single `ProfileToolManager.Factory toolManager`
- **`ProfileManager`**: Replace `toolsManager()`, `collapseFramesManager()`, `removeFramesManager()`, `trimTimeRangeManager()` with single `ProfileToolManager toolManager()`
- **`ProfileManagerImpl`**: `return registry.toolManager().apply(this)`
- **`ProfileFactoriesConfiguration`**: Remove tool factory beans (moved to `ProfileToolFactoriesConfiguration`). Update `profileManagerFactoryRegistry()` to take single `ProfileToolManager.Factory`.
  - Add `@Import(ProfileToolFactoriesConfiguration.class)`
- **`ProfileResource`**: Change `toolsResource()` to access via `profileManager.toolManager()`:
  ```java
  @Path("/tools")
  public ToolsResource toolsResource() {
      ProfileToolManager tools = profileManager.toolManager();
      return new ToolsResource(tools.renameFrames(), tools.collapseFrames(), ...);
  }
  ```
- **`ToolsResource`**: Constructor now takes all individual managers (unchanged in structure, but called differently from ProfileResource)

## Phase 1: Rename Threads (RENAME section)

**Package:** `tools/rename/` | **Complexity:** Low

### Backend
- **`RenameThreadsManager.java`** — interface with `preview(search, replacement)` + `execute(search, replacement)`
- **`RenameThreadsManagerImpl.java`** — delegates to `ProfileToolsRepository`, clears cache
- **Repository methods** (add to `ProfileToolsRepository`):
  - `countThreadsByNameContaining(search)` → `SELECT COUNT(*) FROM threads WHERE name LIKE ...`
  - `previewRenameThreads(search, replacement, limit)` → `SELECT DISTINCT name, REPLACE(name, ...) FROM threads`
  - `renameThreadNames(search, replacement)` → `UPDATE threads SET name = REPLACE(...)`
- **Endpoints:** `POST /tools/rename-threads/preview`, `POST /tools/rename-threads`

### Frontend
- **Models:** `RenameThreadsPreview.ts`, `RenameThreadsResult.ts`
- **Client:** `previewRenameThreads()`, `executeRenameThreads()`
- **Vue:** `ProfileToolsRenameThreads.vue` — clone of RenameFrames, icon `bi-people`
- **Sidebar:** Add to RENAME section below "Rename Frames"

## Phase 2: Delete Events by Type (FILTER section)

**Package:** `tools/deleteevents/` | **Complexity:** Low-Medium

**Purpose:** Permanently remove all events of selected types from the profile. On load, displays a table of all event types present in the profile, sorted by event count (most significant first). The user selects which event types to delete, previews the impact, and confirms deletion. After deletion, orphaned stacktraces, frames, and threads are cleaned up.

### Backend
- **`DeleteEventsByTypeManager.java`** — `listEventTypes()` + `preview(types)` + `execute(types)`
- **`DeleteEventsByTypeManagerImpl.java`**
- **Repository methods:**
  - `listEventTypesWithCounts()` → event types sorted by count descending:
    ```sql
    SELECT et.name, et.label, et.categories, COUNT(e.event_type) AS event_count
    FROM event_types et
    LEFT JOIN events e ON et.name = e.event_type
    GROUP BY et.name, et.label, et.categories
    ORDER BY event_count DESC
    ```
  - `countEventsByTypes(types)` → `SELECT COUNT(*) FROM events WHERE event_type IN (:types)`
  - `deleteEventsByTypes(types)` → `DELETE FROM events WHERE event_type IN (:types)`
  - `deleteEventTypes(types)` → `DELETE FROM event_types WHERE name IN (:types)`
- **Endpoints:** `GET /tools/delete-events/types`, `POST /tools/delete-events/preview`, `POST /tools/delete-events`

### Frontend
- **Models:** `DeleteEventsPreview.ts`, `DeleteEventsResult.ts`, `EventTypeInfo.ts`
- **Client:** `listEventTypes()`, `previewDeleteEvents(types)`, `executeDeleteEvents(types)`
- **Vue:** `ProfileToolsDeleteEvents.vue`
  - On mount: loads event type list via GET, displays table sorted by event count (most significant first)
  - Columns: checkbox, Event Type Name, Label, Category, Event Count
  - "Select All" / "Deselect All" controls
  - Preview shows: total events to delete, percentage of all events, remaining events
  - Confirmation: warns that deletion is permanent and affects all analysis views
  - Icon: `bi-trash3`
- **Sidebar:** Add to FILTER section

## Phase 3: Filter by Thread (FILTER section)

**Package:** `tools/filterthread/` | **Complexity:** Medium

### Backend
- **`FilterByThreadManager.java`** — `listThreads()` + `preview(hashes)` + `execute(hashes)`
- **`FilterByThreadManagerImpl.java`**
- **Repository methods:**
  - `listThreadsWithEventCounts()` → `SELECT t.*, COUNT(e.thread_hash) FROM threads t LEFT JOIN events e ... GROUP BY ...`
  - `countEventsByThreadHashes(hashes)` → `SELECT COUNT(*) FROM events WHERE thread_hash = ANY(:hashes)`
  - `deleteEventsByThreadHashes(hashes)` → `DELETE FROM events WHERE thread_hash = ANY(:hashes)`
- **Thread hash serialization:** Use `String` in REST API (Java `long` → JSON number can lose precision for 64-bit values)
- **Endpoints:** `GET /tools/filter-by-thread/threads`, `POST .../preview`, `POST .../`

### Frontend
- **Models:** `FilterByThreadPreview.ts`, `FilterByThreadResult.ts`, `ThreadInfo.ts` (hash as string)
- **Vue:** `ProfileToolsFilterByThread.vue` — checkbox table (Name, OS ID, Java ID, Virtual, Events)
- **Sidebar:** Add to FILTER section after "Filter by Event Type"

## Phase 4: Trim Stacktrace Roots (TRANSFORM section)

**Package:** `tools/trimroots/` | **Complexity:** Medium

### Backend
- **`TrimStacktraceRootsManager.java`** — `preview(N)` + `execute(N)`
- **`TrimStacktraceRootsManagerImpl.java`** — Java-side array processing:
  1. Fetch all stacktraces
  2. For each: `Arrays.copyOfRange(frameHashes, N, length)` (root-first order, confirmed)
  3. Empty → delete events. Otherwise: recompute hash via `SingleThreadHasher`
  4. Handle merges → `applyStacktraceTransformation()` + orphan cleanup + cache clear
- **Repository methods:**
  - `countTotalStacktraces()`, `findAllStacktraces()` (reuse existing record)
  - `findRootFrameStats(position)` → most common frame at array position (DuckDB 1-based: `frame_hashes[:pos + 1]`)
  - `countStacktracesShorterOrEqual(length)`
- **Endpoints:** `POST /tools/trim-stacktrace-roots/preview`, `POST /tools/trim-stacktrace-roots`

### Frontend
- **Models:** `TrimStacktraceRootsPreview.ts`, `TrimStacktraceRootsResult.ts`
- **Vue:** `ProfileToolsTrimStacktraceRoots.vue` — number input, preview shows root frames table, icon `bi-arrow-bar-up`
- **Sidebar:** Add to TRANSFORM section after "Remove Frames"

## Phase 5: Anonymize Profile (SANITIZE section — new)

**Package:** `tools/anonymize/` | **Complexity:** High

### Backend
- **`AnonymizeProfileManager.java`** — `preview(packagePrefix)` + `execute(packagePrefix)`
- **`AnonymizeProfileManagerImpl.java`**:
  - **Kept prefixes:** `java.`, `javax.`, `sun.`, `jdk.`, `com.sun.`, `org.springframework.`, `org.apache.`, `io.netty.`, `io.grpc.`, `com.google.`, `org.slf4j.`, `ch.qos.logback.`
  - **Kept thread names:** `main`, `Reference Handler`, `Finalizer`, `Signal Dispatcher`, `GC`, `JFR`
  - **Anonymization:** Deterministic XXHash64-based: `class_abcd1234`, `method_ef56`, `thread_78ab`
  - Execute: fetch all frames/threads, compute anonymized names in Java, batch `UPDATE` per row
- **Repository methods:**
  - `findAllFrames()` → `SELECT frame_hash, class_name, method_name FROM frames`
  - `findAllThreads()` → `SELECT thread_hash, name FROM threads`
  - `updateFrame(hash, className, methodName)` per row
  - `updateThreadName(hash, name)` per row
- **Endpoints:** `POST /tools/anonymize/preview`, `POST /tools/anonymize`

### Frontend
- **Models:** `AnonymizePreview.ts`, `AnonymizeResult.ts`
- **Vue:** `ProfileToolsAnonymize.vue` — optional package prefix input, preview shows frame + thread samples, icon `bi-shield-lock`
- **New sidebar section:** SANITIZE (after FILTER)

## Files to Create

**Java (10 new files):**
- `tools/rename/RenameThreadsManager.java` + `RenameThreadsManagerImpl.java`
- `tools/filtereventtype/FilterByEventTypeManager.java` + `FilterByEventTypeManagerImpl.java`
- `tools/filterthread/FilterByThreadManager.java` + `FilterByThreadManagerImpl.java`
- `tools/trimroots/TrimStacktraceRootsManager.java` + `TrimStacktraceRootsManagerImpl.java`
- `tools/anonymize/AnonymizeProfileManager.java` + `AnonymizeProfileManagerImpl.java`

**Java (3 new files for refactoring):**
- `profile-management/.../manager/ProfileToolManager.java`
- `profile-management/.../manager/ProfileToolManagerImpl.java`
- `profile-management/.../configuration/ProfileToolFactoriesConfiguration.java`

**Vue (5 new components):**
- `ProfileToolsRenameThreads.vue`, `ProfileToolsFilterByEventType.vue`, `ProfileToolsFilterByThread.vue`
- `ProfileToolsTrimStacktraceRoots.vue`, `ProfileToolsAnonymize.vue`

**TS models (12 new files):**
- 2 per tool (Preview + Result) = 10 + `EventTypeInfo.ts` + `ThreadInfo.ts`

## Files to Modify

| File | Change |
|------|--------|
| `ProfileToolsRepository.java` | ~15 new methods + records |
| `JdbcProfileToolsRepository.java` | ~15 new SQL queries |
| `StatementLabel.java` | ~15 new TOOLS_* values |
| `ProfileManager.java` | Replace 4 tool methods → single `toolManager()` |
| `ProfileManagerImpl.java` | Replace 4 methods → single delegation |
| `ProfileManagerFactoryRegistry.java` | Replace 4 factory fields → single `ProfileToolManager.Factory` |
| `ProfileFactoriesConfiguration.java` | Move tool beans to new config, add `@Import` |
| `ToolsResource.java` | Add ~13 new endpoint methods |
| `ProfileResource.java` | Update `toolsResource()` to use `profileManager.toolManager()` |
| `ProfileToolsClient.ts` | Add ~13 new methods |
| `router/index.ts` | 5 new routes |
| `ProfileDetail.vue` | Add sidebar items + new SANITIZE section |

## Verification

1. `mvn compile` — all Java compiles
2. `npm run build` — frontend builds
3. Existing tools (Rename Frames, Collapse, Remove, Trim) still work after refactoring
4. Each new tool's preview + apply cycle works
