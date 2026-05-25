# Profile-wide IDE Target Control — Design

- **Date:** 2026-05-25
- **Status:** Proposed (awaiting implementation plan)
- **Area:** `jeffrey-microscope` — `core-microscope` (backend IDE bridge) + `pages-microscope` (frontend)

## Goal

Today an IDE target is chosen for a profile (cached server-side by `selectTarget`) and then
silently reused for every "Open in IDE" / "View Source" jump — there is **no UI to see which IDE
window is linked or to change it**, and the affordance only exists inside the flamegraph tooltip.

Surface the linked IDE target **centrally for the whole profile**, on every mode and page, with a
way to change it or disconnect — modelled on the existing **"Secondary Profile"** control — and make
the feature **self-bootstrapping** (no hidden enable flag): if nothing is linked, the panel guides
the user to detect a running IntelliJ or install the Jeffrey IntelliJ Plugin.

## Decisions (locked with the user)

1. **Placement:** a control in `ProfileDetail.vue`'s top `feature-collection-nav`, as a sibling of
   the "Secondary Profile" toggle (pinned right via `margin-left:auto`). Profile-global.
2. **Bar is compact:** the nav button shows only a status pill — `LINKED` (green) / `SET UP`
   (neutral grey, *not* an alarming amber, since "not configured yet" is not an error). The
   IDE/project name lives in the detail panel, not the bar.
3. **Interaction mirrors Secondary Profile:** the nav button is a **toggle** that reveals/hides a
   **detail bar in the content area** (the `.compact-comparison-bar` pattern), not a dropdown.
   Switching always goes through the existing `IdeTargetPickerModal`.
4. **No enable property.** Drop `jeffrey.microscope.ide.enabled`. Fold the on/off decision into the
   existing `jeffrey.microscope.ide.mode` property: `default | jfr-profiler-plugin | off`
   (default = `default`). The control is **always shown** in `default` mode; the plugin install /
   target selection is the de-facto enablement.
5. **Status is cache-only — no port scan on profile mount.** Linked stays linked, read straight from
   the per-profile cache. No "running/offline" probing, no green/grey running dot.
6. **Discovery (port scan) runs in exactly three situations:** the user clicks **Detect /
   Select / Change target**; or a real **jump request fails** because the IDE is unreachable.
7. **Jump-failure recovery:** on an unreachable jump, run **one** discovery and re-resolve the cached
   project to its (possibly new) port — match by `projectId` (locationHash) first, then fall back to
   the cached `projectName` — and retry silently. Only if the project is genuinely gone do we show a
   toast offering **Select IDE target**. A reachable IDE that simply can't resolve the symbol is a
   "location not found" message, **not** a re-target prompt.
8. **Panel defaults to closed** (state remembered per session in `sessionStorage`, like the
   comparison panel).
9. **Disconnect** clears the per-profile cache → bar returns to `SET UP` / onboarding, and the next
   jump prompts to select a target.
10. **Linked card** = single-row "Option 2": tile + `IntelliJ IDEA` + a green `Badge`
    (`variant="success"`, `icon="bi bi-circle-fill"`, `value=projectName`, `:uppercase="false"`,
    `size="s"`) + monospace location + ghost **Change** / **Disconnect**.
11. **Not-linked card** = onboarding: "Detect IntelliJ" + an "Install the Jeffrey IntelliJ Plugin"
    link (JetBrains Marketplace URL — TBD, to be supplied). After a failed detect, an info note
    explains nothing was found.

## Mode semantics (replaces the `enabled` boolean)

| `ide.mode`            | Bridge                     | `isEnabled()` (tooltip + jumps) | `selectable` (nav control) |
|-----------------------|----------------------------|---------------------------------|----------------------------|
| `default`             | `JeffreyPluginBridge`      | true                            | true                       |
| `jfr-profiler-plugin` | `JfrProfilerPluginBridge`  | true                            | false                      |
| `off`                 | `DisabledIdeBridge` (new)  | false                           | false                      |

- **`isEnabled()` now means `mode != off`.** It still gates the flamegraph tooltip "Open in IDE" /
  "View Source" buttons, which therefore keep working in **both** `default` and `jfr-profiler-plugin`
  modes (the tooltip is mode-agnostic — explicitly **not** coupled to the new nav control).
- **`selectable` means `mode == default`** — only the multi-window Jeffrey plugin supports choosing a
  target window, so the nav control + detail panel render only in `default` mode.

## Backend changes (`core-microscope`, package `…core.manager.ide`)

### `IdeMode`
Add `OFF` (property value `off`). `fromProperty` maps unknown/`off` accordingly; default stays
`default`.

### `IdeTarget` (record) — cache the display name
Extend so the name survives the IDE being closed and is shown without discovery:

```java
public record IdeTarget(int port, String projectId, String ideName, String projectName) {}
```

### `IdeTargetStatus` (new record) — cache-only status view
```java
public record IdeTargetStatus(boolean selectable, boolean linked, String ideName, String projectName) {
    static IdeTargetStatus notSelectable() { return new IdeTargetStatus(false, false, null, null); }
    static IdeTargetStatus notLinked()     { return new IdeTargetStatus(true,  false, null, null); }
}
```

### `IdeOpenResult` / `IdeSourceResult` — distinguish "re-target" failures
Carry a reason (suggested enum `IdeFailureReason { NONE, DISABLED, NO_TARGET, TARGET_UNAVAILABLE,
NOT_RESOLVED, SOURCE_UNAVAILABLE }`). The frontend offers **Select IDE target** for `NO_TARGET` /
`TARGET_UNAVAILABLE`; for `NOT_RESOLVED` / `SOURCE_UNAVAILABLE` it shows only the message.

### `IdeBridge` (interface)
```java
boolean isEnabled();                                                         // == mode != off
IdeOpenResult open(IdeOpenRequest request);
IdeSourceResult fetchSource(IdeSourceRequest request);
IdeTargetsResult discoverTargets(String profileId, String fqn);              // unchanged (picker only)
boolean selectTarget(String profileId, int port, String projectId,
                     String ideName, String projectName);                    // signature extended
default IdeTargetStatus targetStatus(String profileId) {                     // NEW — default unsupported
    return IdeTargetStatus.notSelectable();
}
default boolean clearTarget(String profileId) { return false; }              // NEW
```

### `JeffreyPluginBridge`
- Drop the `enabled` constructor param; `isEnabled()` returns `true` (it's only built in `default` mode).
- `selectTarget(...)` — store the names: `cache.put(profileId, new IdeTarget(port, projectId, ideName, projectName))`.
- `targetStatus(profileId)` — **cache read only, no discovery**: cached → `new IdeTargetStatus(true, true, ideName, projectName)`; else `IdeTargetStatus.notLinked()`.
- `clearTarget(profileId)` — `cache.clear(profileId)`.
- `open(...)` / `fetchSource(...)` — replace the eager `resolveTarget()` (which scans every call) with:
  1. `cached = cache.get(profileId)`; if `null` → `failed(NO_TARGET)`.
  2. Call the plugin **directly on `cached.port()`** (no discovery).
  3. Reachable + resolved → success. Reachable + not resolved → `failed(NOT_RESOLVED)` (no re-target).
  4. **Unreachable** (client returns `null`) → `reresolve(profileId, cached)`: one `discover()`, find
     the project whose `id == cached.projectId()` (fallback `name == cached.projectName()`), update
     the cache with the new port, retry once. Still unreachable / gone → `failed(TARGET_UNAVAILABLE)`.

### `JfrProfilerPluginBridge`
Drop the `enabled` constructor param; `isEnabled()` returns `true`. Keeps interface defaults →
`targetStatus = notSelectable()`, `clearTarget = false`, extended `selectTarget` no-ops. The nav
control is hidden in this mode, but the tooltip buttons / jumps work via `baseUrl`.

### `DisabledIdeBridge` (new)
All operations no-op; `isEnabled()` returns `false`. Used for `mode = off`.

### `IdeController` (`/api/internal/ide`)
- `GET /status?profileId=…` → `IdeTargetStatus` (cache read).
- `DELETE /target?profileId=…` → `clearTarget`, returns `{ success }`.
- `POST /target` — request body gains `ideName`, `projectName`.

### `AppConfiguration`
`ideBridge(...)` switch gains the `OFF → new DisabledIdeBridge()` case; `DEFAULT`/`JFR_PROFILER_PLUGIN`
no longer pass an `enabled` flag. Remove the `jeffrey.microscope.ide.enabled` `@Value`. (Existing
`IdeConfigClient`/`/config/ide` exposes `enabled = isEnabled()`.) `IdeTargetCache` already provides
`get/put/clear`; in-memory, resets on Microscope restart (unchanged).

## Frontend changes (`pages-microscope`)

### `services/api/IdeClient.ts`
- `getStatus(profileId): Promise<IdeTargetStatusResponse>` → `GET /status`.
- `clearTarget(profileId): Promise<{ success: boolean }>` → `DELETE /target`.
- `selectTarget(profileId, port, projectId, ideName, projectName)` — extended.
- new `IdeTargetStatusResponse { selectable, linked, ideName, projectName }`; `IdeOpenResponse` gains `reason`.

### `stores/ideProfileTargetStore.ts` (new)
Per-profile reactive state + orchestration:
- `status: Ref<IdeTargetStatusResponse>`; `load(profileId)` (calls `getStatus`).
- `selectOrChange(profileId)` — `discoverTargets(profileId, '')`; empty → toast "No running IntelliJ
  found"; otherwise **always** open `ideTargetPickerStore` (pre-selecting the cached project) —
  **never auto-select** — then `selectTarget(…names…)`, emit `MessageBus.IDE_TARGET_CHANGED`, reload.
- `disconnect(profileId)` — `clearTarget`, emit `IDE_TARGET_CHANGED`, reload.
- listens to `IDE_TARGET_CHANGED` so jump-flow selections keep the bar in sync.

### `services/IdeTargetService.ts` / `IdeJumpService.ts`
- Drop eager auto-select/discovery per jump. New flow:
  - **Not linked** → `store.selectOrChange(profileId)`; cancelled → abort; else `open`.
  - **Linked** → `IdeClient.open(...)` directly (backend uses cache + re-resolves once on unreachable).
    - `reason ∈ {TARGET_UNAVAILABLE, NO_TARGET}` → toast with **Select IDE target** → `selectOrChange` → retry `open` once.
    - `reason ∈ {NOT_RESOLVED, SOURCE_UNAVAILABLE}` → plain message toast (no re-pick).
- `selectTarget` calls pass `ideName`/`projectName` resolved from the chosen instance/project.

### `components/IdeTargetBar.vue` (new)
Content-area detail panel, styled from existing `.compact-comparison-bar` / `.compact-card` classes
(no new `:root` tokens):
- **Linked** → single-row card: `🧩` tile, `IntelliJ IDEA`, `<Badge variant="success"
  icon="bi bi-circle-fill" :value="projectName" :uppercase="false" size="s" />`, mono
  `localhost:<port> · PID <pid>`, ghost **⟳ Change** (`selectOrChange`) + **✕ Disconnect**
  (`disconnect`, with confirm).
- **Not linked** → onboarding: "Detect IntelliJ" (`selectOrChange`) + "Install the Jeffrey IntelliJ
  Plugin ↗" (marketplace link); after an empty detect, an info note.

### `views/profiles/ProfileDetail.vue`
- `ideConfigStore.loadOnce()`; `ideProfileTargetStore.load(profileId)` on mount.
- `ideTargetPanelVisible` ref, persisted to `sessionStorage`, **default `false`**.
- In `.nav-container`, before `.comparison-toggle-wrapper`: an IDE toggle button (reusing
  `comparison-toggle-btn` styling) showing `LINKED` / `SET UP` from `store.status`, toggling
  `ideTargetPanelVisible`. Rendered only when `ideConfigStore.isEnabled() && store.status.selectable`.
- Render `<IdeTargetBar :profile-id="profileId" />` in main content (above the router-view) when
  `ideConfigStore.isEnabled() && store.status.selectable && ideTargetPanelVisible`.

### `services/MessageBus.ts`
- Add `IDE_TARGET_CHANGED = 'ide-target-changed'`.

### Flamegraph tooltip (unchanged behaviour)
`FlamegraphTooltip.ide_action` / `Tooltip.ts` keep gating on `ideConfigStore.isEnabled()` (now
`mode != off`), so the buttons work in `default` **and** `jfr-profiler-plugin` modes. Only their
failure handling benefits from the shared jump flow's re-pick prompt (default mode).

## Data flow

- **Mount:** `loadOnce` config → if `enabled && selectable`, `getStatus` (cache read) → render button
  (`LINKED` / `SET UP`). Panel closed.
- **Open panel → Detect/Change:** `discoverTargets('')` → picker → `selectTarget(names)` → emit →
  status reloads → linked card.
- **Disconnect:** `clearTarget` → emit → status reloads → `SET UP` / onboarding.
- **Jump (linked):** `open` on cached port; unreachable → backend re-resolves once by project →
  retry; gone → `TARGET_UNAVAILABLE` → toast offers Select → picker → retry. Any successful select
  anywhere emits `IDE_TARGET_CHANGED`, so the nav stays in sync.

## Non-goals / out of scope

- Persisting the target across Microscope restarts (cache stays in-memory, as today).
- Probing/monitoring whether the IDE is currently running.
- Changing what the flamegraph tooltip buttons *do* (only their gate moves from `enabled` to
  `mode != off`; they remain available in both plugin modes).

## Testing

- **Backend:** `IdeController` integration test for `GET /status`, `DELETE /target`, extended
  `POST /target`. `JeffreyPluginBridge` unit tests: `targetStatus` reads cache without discovery;
  `clearTarget`; `open` direct-hit success, unreachable → re-resolve-by-projectId/name → retry,
  truly-gone → `TARGET_UNAVAILABLE`, reachable-but-unresolved → `NOT_RESOLVED`. `DisabledIdeBridge`
  `isEnabled() == false`. `AppConfiguration` mode → bridge mapping (incl. `off`).
- **Frontend:** `ideProfileTargetStore` (`selectOrChange` always opens picker / never auto-selects;
  `disconnect`); `IdeTargetBar` renders linked / onboarding / post-failed-detect states.

## Open questions

- **JetBrains Marketplace URL** for the "Install the Jeffrey IntelliJ Plugin" link — to be supplied.
- Confirm the exact `IdeFailureReason` shape during the plan, and whether `selectable` is exposed via
  `/status` only (current design) or also folded into `/config/ide`.
