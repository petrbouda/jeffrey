# Jeffrey MCP → Spring AI 2.1.0 — Phase 0 spike

Throwaway spike proving the **runtime** gates of the migration (the API-level gates are already
confirmed by inspecting the 2.1.0 / 2.2.0 jars — see the plan). It is a standalone Spring Boot app
on **Spring AI 2.1.0's modern (stateless `2026-07-28`) MCP server over Spring MVC**, configured the
way Jeffrey needs:

- **streamable-HTTP with per-method SSE** — fast tools register no `McpSyncNotifier` and answer as a
  single JSON POST (the cheap path); the long-running `sample_progress` returns a *streaming* response
  and emits `notifications/progress` over `text/event-stream`. This mirrors the migration decision:
  pure-POST everywhere except where progress is genuinely useful (the `operations_*`/`tasks` replacement).
- a **custom `McpSyncToolRepository`** (`SpikeToolRepository`) — the hook that replaces Jeffrey's
  `ProfileScopedToolset` (per-profile `find`/`list`).
- three tools registered **programmatically with a hand-built CLOSED (`$ref`-free) schema** and
  `jeffrey/*` `_meta`: `sample_ping` (read), `sample_confirm` (elicitation / `input_required`), and
  `sample_progress` (long-running, streams SSE progress).

> ⚠️ Not compiled in the authoring container (no Java 25) — written from verified SDK signatures.
> One spot is marked `TODO(first CI build)`: exactly how the elicitation answer is surfaced on retry.
> Expect a possible tiny fixup on first build.

## Prerequisites

- Java 25, Maven.
- Node 22+ (for the conformance suite).

## Build & run

```bash
cd mcp-spike
mvn -q spring-boot:run     # serves POST http://localhost:8585/api/mcp
```

## Gate 1 — official conformance suite

```bash
URL=http://localhost:8585/api/mcp
npx --yes @modelcontextprotocol/conformance@0.2.0-alpha.12 server \
    --url "$URL" --spec-version 2026-07-28 --suite active
npx --yes @modelcontextprotocol/conformance@0.2.0-alpha.12 server \
    --url "$URL" --spec-version 2026-07-28 --scenario server-stateless
```
The suite's `wire-schema-valid` check validates every message the server emits against the official
`2026-07-28` schema — this is the real test that our hand-built closed schema is accepted. Its own
fixture-tool checks (`test_*`) fail for any real server, exactly as Jeffrey's current
`build/mcp/conformance-expected-failures.yml` already records, so the CI job runs it informationally.

## Gate 2 — the real coding agents connect

**Claude Code (v2 runtime):**
```bash
MCP_SDK_GENERATION=v2 claude mcp add --transport http jeffrey-spike http://localhost:8585/api/mcp
# then in a session: list tools, call sample_ping, trigger sample_confirm, and call
# sample_progress (confirm the client consumes the streamed SSE progress, not just plain POST)
```

**Codex (v0.147.0+, flag on):** `~/.codex/config.toml`
```toml
[features]
mcp_2026_07_28 = true

[mcp_servers.jeffrey-spike]
url = "http://localhost:8585/api/mcp"
```

Gemini CLI is out of scope (its client doesn't speak `2026-07-28` yet).

## What a green run proves

Tools with Jeffrey's closed schema + `_meta`, structured output, the elicitation round-trip, and
SSE progress streaming all work on Spring AI's modern server (mixed plain-POST + per-method SSE), and
the two supported agents connect and consume both response styles. That clears the last go/no-go
before porting the 29 `*McpTools` families onto the custom repository.

## skills/* and tasks/* (API-proven, not wired into this spike)

Not a runtime risk, so left out to keep the spike focused. `skills/*` is preserved as a custom
`io.modelcontextprotocol.modern.server.McpFeature` — a generic extension point:

```java
public interface McpFeature {
    Set<String> methods();                               // e.g. {"skills/list","skills/get"}
    Mono<? extends McpAsyncResponse<? extends McpSchema.Result>> handle(McpRequestContext, Object);
    default void capabilities(McpSchema.ServerCapabilities.Builder b) { /* advertise in discover */ }
}
```
Register via an `McpServerCustomizer<McpServer.Builder>` bean: `builder.feature(new SkillsFeature(...))`.
(`tasks/*` is replaced by native `notifications/progress` over SSE — see the plan.)
