---
paths:
  - "jeffrey-microscope/core-microscope/**/mcp/**"
  - "jeffrey-microscope/profiles/mcp-server/**"
  - "jeffrey-claude-plugin/**"
  - ".claude-plugin/**"
  - ".agents/**"
---

## MCP server and plugin rules

### One AI integration, one direction
- Jeffrey never calls a model provider. No in-app assistant, chat, OQL helper, provider setting or secret storage — every earlier one is now a plugin skill over the MCP server. A feature that would put a model *inside* Jeffrey must be a skill or a tool instead.
- The endpoint is `POST /api/mcp` (`ExternalMcpController`), installation-wide, `profileId` as a tool argument. `POST /api/internal/mcp` (`LEGACY_PATH`) answers identically because clients have it configured. It is the only MCP endpoint; the loopback `claude-code` one is gone.
- Off only by `jeffrey.microscope.mcp.enabled` (read at startup). `hubs_` and `ide_` have their own switches (`…mcp.hubs.enabled`, `…mcp.ide.enabled`) because they reach outside the server. `…mcp.families` narrows what is advertised; `…mcp.preset` (`all|jfr|heap|hub`) picks a selection when that list is empty; `operations` must stay selected wherever a writer is.
- No auth: `McpRequestGuard` rejects a `Host` outside `jeffrey.microscope.mcp.allowed-hosts` (loopback by default) before checking `Origin`.

### Protocol layer (`profiles/mcp-server`) is the only place that knows the protocol
- `AbstractMcpStreamableHttpController`, `ReflectiveToolset` (`@Tool`→MCP), `ProfileScopedToolset`/`CompositeToolset`, prompts/resources/completions providers, `McpServerFeatures`. Do not re-implement any of it in a controller. Spring AI survives only for `@Tool`/`@ToolParam`.
- POST-only and stateless on purpose: `GET`/`DELETE` → 405, no `Mcp-Session-Id`, no SSE. Long work is polled through `operations_*`, never `notifications/progress`.
- `initialize` must answer even when assembling the toolset would fail — nothing on that path may resolve the toolset (`McpCompletions.isAvailable()` reads configuration for that reason).
- JSON-RPC batching only on `2024-11-05` and `2025-03-26`; removed in `2025-06-18`.
- A tool's `title` is derived from its name in `McpToolSpec`; do not annotate it per method.

### Tool hints are per tool, not per family
- Nine tools are not read-only, none changes an analysed profile: `recordings_analyzeFile`, `recordings_analyzeRecording`, `recordings_delete` (the only `destructiveHint`), `heap_prepare`, `hubs_download`, `hubs_fetchFile`, `operations_cancel`, `ide_link`, `ide_open`.
- `McpToolsetAssemblerTest` pins the write set **and the total tool count (111)**; the read-only count is repeated in prose in `DocsIndexPage.vue`, so change both when a tool is added or removed.
- Findings from judging tools (`jvm_autoAnalysis`, `jvm_container`) use `McpFinding` (id `category:subject`) so the same condition from two tools merges; `profiles_summary` carries `capabilityGaps` from `ProfileCapabilityGaps`.

### Artifacts are handed over, not parsed
- `hubs_files` lists a session's artifacts (`gc.jvm-log`, `perf-counters.hsperfdata`, `.log`, `hs-jvm-err.log`); `hubs_fetchFile` pulls one and answers with its **absolute path** — the agent greps it itself. Do not build a server-side log/crash parser or an `artifacts` table; both existed and were removed.
- No catalogue of fetched files: the path is deterministic (`profiles/<profileId>/artifacts/<name>`, or `artifacts/<hub>/<project>/<session>/<name>` for a session with no recording). A file fetched before analysis is moved beside the profile on the next fetch. Fetch answers from disk before reaching for the hub.
- Both path elements come off the wire (session id, file name): reduce each to a single path element and check the result is inside its base directory before writing.
- All `hubs_` deadlines share one timer, `McpDeadlines`. No private schedulers.
- Known gap: `~/.jeffrey-microscope/artifacts/<hub>/<project>/<session>/` has no retention.

### `hubs_download` windows
- Whole session, or `startTime`/`endTime` → only the chunks covering the window (`ChunkWindow`: file *n* covers up to the start of *n+1*), or explicit `fileIds`. A partial download is tagged `origin.window` so `DownloadedSessionIndex` never reports it as the session's local copy. Contiguity: see `hub-repository.md`.

### The `microscope` plugin (`jeffrey-claude-plugin/`)
- One set of skills, four manifests: Claude Code (`.claude-plugin/plugin.json`, configurable endpoint), Agent Plugins (`plugin.json` + `mcp.json`, fixed `localhost:8585`), Codex (`.codex-plugin/plugin.json`), Gemini (`gemini-extension.json`, `${JEFFREY_MCP_ENDPOINT:-…}`).
- Skills are also served as MCP prompts; the profile catalogue as MCP resources; `McpInstructions` at `initialize` — a client with no plugin still gets an account of how to use the tools.
- Subagents do not survive the portable format: Codex TOMLs in `codex/agents/`, Gemini in `gemini/agents/` (hand-copied — Gemini rejects `disallowedTools`/`skills`/`color` frontmatter, accepts only `mcp_<server>_<tool>` patterns, and a Gemini subagent cannot dispatch another, so there is no Gemini `profile-lead`).
- The `report` skill is the evidence discipline every skill and agent writes to.
