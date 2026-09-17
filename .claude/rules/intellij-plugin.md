---
paths:
  - "jeffrey-intellij-plugin/**"
  - "jeffrey-microscope/core-microscope/**/ide/**"
  - "jeffrey-microscope/core-microscope/**/IdeRecordingLookup*.java"
  - "jeffrey-microscope/pages-microscope/src/router/profile-routes.json"
---

## IntelliJ plugin rules

### Build
- Standalone Gradle project, deliberately outside the Maven reactor. Runs on **Java 21** (JetBrains Runtime), not Jeffrey's 25.
- Java-PSI and platform APIs only — no dependency on the Kotlin plugin or Git4Idea, so it loads in any IDE.
- The Terminal plugin and `com.intellij.modules.jcef` are **optional** dependencies; `AgentLaunchers` degrades to copying the command, `RecordingPanel.createRenderer` catches the `LinkageError` from the first mention of `CefPanelRenderer`.

### It never renders profile data
- Anything that would show a reader their profile is a link to Microscope at a profile-scoped view URL (`/profiles/{id}/{view}`, the same ones `profiles_viewLink` hands out). No flame graphs, charts, hot-method lists, gutter markers with figures.
- The one bounded exception is the recording panel: **four figures and the auto-analysis lines, nothing else** (window, sample count, event type count, sample-loss share; one line per finding, titled by the rule, no topic grouping). No fifth figure. All from one call, `GET /api/internal/recordings/by-path?path=&sizeInBytes=` (`IdeRecordingLookup`), which never imports as a side effect and matches by file name + byte size.
- `resolve` exists separately from `navigate` because an agent grounding a finding must not move the developer's cursor. `ide_resolve` never moves the editor; `ide_open` does.

### The recording panel
- `RecordingFileEditorProvider` needs `RecordingFileType`: an unregistered extension is not routed through the editor system (on Ultimate a double-click reaches the bundled profiler instead). `.hprof` is claimed too, beside IntelliJ's own viewer.
- Kind (recording vs heap dump, `IdeRecordingStateResponse.Kind`) comes from **what was double-clicked**, not from the profile. It decides the figures, the tiles, the agent phrase and where *Open in Microscope* lands (`ProfileSummary.landingPath()`: `dashboard` vs `heap-dump/overview` — the bare `/profiles/{id}` redirects to the JFR dashboard blindly).
- An un-indexed dump reports `cacheReady: false`: callout with *Build index* (posts `heap/initialize-all`, polls `heap/init-progress` every 2 s, `HeapIndexBuild` reduces the pipeline to one line); tiles are drawn off via `ProfileSummary.indexMissing()`.
- Tile paths are pinned by `ProfileRouteManifestTest` against `src/router/profile-routes.json`, generated from `profileChildRoutes.ts` by a Vitest snapshot and copied in by Gradle — the router's catch-all makes a wrong path look like a working link.
- Findings arrive with the profile; the panel draws them once and **never polls**. `analysisComputed` = cache key present (an empty run reads `Nothing flagged.`); `analysisPossible` separates a failed rule set (offer to run again) from a recording Microscope no longer has (offer nothing).
- Two buttons deliberately absent: *Analyze again* on a ready profile, and *Settings…* anywhere but the unreachable/failed states.

### Rendering
- `RecordingPanel` draws nothing; it owns the Microscope conversation, the state machine and `PanelActions` (an interface of eleven operations on purpose — see its comment). A `PanelRenderer` turns state into pixels; it is deliberately **not sealed** (the JCEF renderer lives in `recording.web`).
- `web/CefPanelRenderer` hosts a `JBCefBrowser` fed one document by `WebPanelHtml` + `WebPanelStyles` (theme emitted once as `:root` custom properties). **`--u` carries `px`** — `calc(16*1.0)` is a number, CSS drops it silently, and the panel renders unstyled.
- `SwingPanelRenderer` is the fallback for no-JCEF only; not held to visual parity.
- Nothing in the document is an `<a href>`: every control carries `data-action`, one delegated listener sends it over a single `JBCefJSQuery`. Each browser is a `Disposable` registered with the tab. `LafManagerListener` re-renders on theme change.
- Icons are inline SVG in `web/PanelSvg` (Chromium cannot use the Swing `<icon>` factory; `AllIcons` paths move).

### Comparison and agent hand-off
- `PanelState` is the pair (primary + optional baseline); the tab's own file is **always** the primary — *Swap* reopens the other tab. `Comparability` reports window ratio past `WINDOW_TOLERANCE` and event-type-count mismatch only; no deltas (that is Microscope's job). Differential routes read `?baseline=<profileId>` (`BaselineQuery.ts`). Heap dumps never compare here.
- The agent gets `<cli> "Analyse Jeffrey profile <id>"` — profileId, never the path, no question of its own. `AgentTask` is a sealed triple (`AnalyseRecording`, `AnalyseHeapDump`, `Compare`), each phrase the trigger for a different skill. Agents are `AgentCli.ALL` with a `PromptStyle` each (Gemini needs `-i` to stay interactive). One split button; primary = last launched (`JeffreySettings.preferredAgent`).
