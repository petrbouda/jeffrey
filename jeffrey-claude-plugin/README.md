# Microscope plugin

Read JVM profiles from a running [Jeffrey Microscope](https://www.jeffrey-analyst.cafe/docs/microscope)
without leaving your terminal: point your coding agent at a `.jfr` file in your repository and it
analyses it, then lists the recordings you have analysed, queries their DuckDB tables, and pulls
flamegraph, trace and heap-dump exports straight into a session in your own repository — so the
profile and the source code are in front of the same reader.

One package, two plugin formats. **Claude Code** reads `.claude-plugin/plugin.json`; **Codex** and
the other [Agent Plugins](https://agent-plugins.org/) clients read the root `plugin.json` and
`mcp.json`. The skills and the MCP server underneath are the same files for both.

Every analysis tool is **read-only**. The exceptions are `recordings_` and `hubs_`, which create
profiles rather than changing them, and which a Jeffrey can switch off with
`jeffrey.microscope.mcp.ingest.enabled=false` and `jeffrey.microscope.mcp.hubs.enabled=false`.

Full documentation: [Microscope MCP](https://www.jeffrey-analyst.cafe/docs/microscope-mcp) —
[Claude Code](https://www.jeffrey-analyst.cafe/docs/microscope-mcp/claude-code),
[Codex](https://www.jeffrey-analyst.cafe/docs/microscope-mcp/codex),
[other clients](https://www.jeffrey-analyst.cafe/docs/microscope-mcp/other-clients).

## Install

Jeffrey's MCP server is **on by default** — a running Jeffrey is already serving it.
**Settings → Coding Agents (MCP)** reports whether the endpoint is serving and shows the connection
details for this installation.

**Claude Code:**

```
/plugin marketplace add petrbouda/jeffrey
/plugin install microscope@jeffrey
```

**Codex:**

```bash
codex plugin marketplace add petrbouda/jeffrey
```

then `/plugins` in Codex, or work from a clone with `codex plugin marketplace add ./jeffrey`.

Either client can also skip the plugin and register the endpoint by hand — the docs above cover
`claude mcp add`, `codex mcp add`, and the raw JSON-RPC for anything else.

## Pointing it at your Jeffrey

The plugin ships with the endpoint set to `http://localhost:8585/api/internal/mcp`.

In **Claude Code** anywhere else — a different port, a container, an SSH tunnel — is a setting:
Claude Code offers the field when you enable the plugin, `/plugin` reopens it afterwards, and the
value lives per machine in `~/.claude/settings.json`.

```
Jeffrey MCP endpoint: http://localhost:9000/api/internal/mcp
```

**Codex has no equivalent.** The Agent Plugins format forbids placeholder expansion in a server URL,
so a plugin-provided endpoint is fixed at `localhost:8585`. For any other address, disable the
plugin's server and register your own in `~/.codex/config.toml`:

```toml
[mcp_servers.jeffrey]
url = "http://localhost:9000/api/internal/mcp"
```

The skills keep working; only the server registration moves.

## What you get

**Tools**, in sixteen families:

| Family | What it does |
|---|---|
| `profiles_` | The catalogue: which recordings are analysed, what each one can answer, and deep links into the UI |
| `flamegraph_` | Which graphs a profile supports, and the call tree as Markdown |
| `compare_` | Two profiles against each other: whether they are comparable, what moved, and the differential call tree |
| `traces_` | Trace operations, the application's own notifications, exemplars, span trees, span-scoped flamegraphs, and the attributes that say which population a trace belonged to |
| `jvm_` | The machine underneath: garbage collection, safepoints, JIT compilation, threads, thread dumps, native memory, the container, the JVM flags and what it was started with |
| `http_` | The HTTP traffic the application served: percentiles, endpoints, status codes, slowest requests |
| `jdbc_` | Statement timings and groups, and the connection pool in front of them |
| `grpc_` | gRPC latency per service and method, and the message sizes moved |
| `methodtracing_` | Instrumented method timings (JEP 520): by cost, the worst invocations, the JVM's aggregates |
| `io_` | Socket and file I/O: bytes, targets and the slowest operations — the waiting a CPU graph cannot see |
| `blocking_` | Contended monitors, waits, parks and virtual-thread pinning |
| `timeline_` | When the samples landed: the busiest windows, and sub-second zoom inside one |
| `memory_` | Allocation by type, and JFR-side leak candidates that need no heap dump |
| `jfr_` | The profile's DuckDB tables — schema and read-only SQL |
| `heap_` | Heap summary, class histogram, dominator tree, leak suspects, GC-root paths, a two-dump diff, and read-only SQL |
| `recordings_` | One of the two that write: imports a recording file and builds a profile from it |
| `hubs_` | The recordings still on a connected Jeffrey Hub: lists sessions across every hub and pulls one in |

`recordings_analyzeFile` takes an **absolute path**, and the file has to be on the machine Jeffrey
runs on — Jeffrey opens it, the client does not upload it. That is the usual case (one laptop running
both) and not the case for a Jeffrey in a container or on another host.

**Skills**, which the agent picks up on its own and you can also invoke directly —
`/microscope:analyze-jfr` in Claude Code, `$analyze-jfr` in Codex:

- `analyze-jfr` — where to start and which family answers which question
- `analyze-heap` — a heap dump end to end: what is holding the memory, what is leaking,
  which class loader never went away, and the order the heap tools have to be run in
- `analyze-hub` — the recordings that never reached this machine: finds a session across the
  connected Jeffrey Hubs, pulls it in, and hands off to `analyze-jfr` or `analyze-heap`
- `compare-jfr` — before against after: whether a change made it slower, which methods
  moved, and whether the two recordings were comparable in the first place
- `advise-jfr` — from a profile to a code change: the hottest CPU, wall-clock, allocation and
  blocking frames mapped to real source in your checkout, a recommendation, then the edit and a
  re-profile on request
- `jfr-sql` — the JFR schema and the DuckDB idioms that go with it
- `heap-sql` — the heap-dump index schema

The exports carry their own reading instructions, so the skills stay short: they cover the
workflows and the two schemas, not things the tool output already explains.

**One analyst agent**, `profile-analyst`. A single flamegraph export can run to 120,000 characters,
and a question usually takes several. The analyst runs a sequence and returns only the findings — the
hot frames with their shares, or the retaining classes with their GC-root paths — leaving everything
it read in its own context. The skills hand it the reading and keep what needs your conversation:
mapping frames onto the checkout, the recommendation, and every question put to you.

Claude Code gets it from the plugin as `microscope:profile-analyst`, restricted to the read-only MCP
families so it cannot touch your files, import a recording or propose an edit. **Agent Plugins
defines only skills and MCP servers**, so Codex cannot receive an agent from a plugin — copy
[`codex/agents/profile-analyst.toml`](codex/agents/profile-analyst.toml) to `~/.codex/agents/`
instead. That version is sandboxed read-only, but its tool restriction is instruction-level rather
than enforced.

## Permissions

Both clients ask before each tool the first time. Every Jeffrey tool except `recordings_` and
`hubs_` is read-only, so approving the family once is usually what you want.

Claude Code, from the prompt or up front with `/permissions`:

```
mcp__plugin_microscope_jeffrey__*
```

Codex, in `~/.codex/config.toml`:

```toml
[mcp_servers.jeffrey]
default_tools_approval_mode = "auto"
```

## Try it

With a profile analysed in Jeffrey:

> list the Jeffrey profiles, then show me where the CPU time goes in the most recent one

Or, starting from a recording that is not in Jeffrey yet:

> analyze target/checkout-run.jfr in Jeffrey and tell me which of my methods dominate the profile

> the `GET /api/orders` operation is slow — find a slow example and tell me what the JVM was doing
> inside its slowest span

Or, from a heap dump:

> analyze /tmp/heap.hprof in Jeffrey — what is holding the memory, and is anything leaking?

Or, once the hotspot is known, in the repository that produced it:

> advise on the most recent Jeffrey profile — what should I change in this repo?

## Package layout

```
jeffrey-claude-plugin/
├── plugin.json               Agent Plugins 1.0.0 manifest — Codex, Cursor, Copilot, VS Code, Kiro
├── mcp.json                  Agent Plugins MCP config — the streamable-http endpoint
├── .claude-plugin/
│   └── plugin.json           Claude Code manifest — same plugin, with the configurable endpoint
├── .codex-plugin/
│   └── plugin.json           Codex-native manifest, pointing at the same skills and mcp.json
├── skills/                   Seven skills, read by both formats
├── agents/
│   └── profile-analyst.md    Claude Code subagent
└── codex/agents/
    └── profile-analyst.toml  The same analyst as a Codex custom agent, installed by hand
```

The directory keeps its `jeffrey-claude-plugin` name so existing installs and marketplace entries
keep resolving; it serves both clients.

## Security

The MCP endpoint has no authentication yet, exactly like the rest of Jeffrey's API: anyone who can
reach the address can read every profile in that installation. Keep Jeffrey bound to localhost, or
put it behind an SSH tunnel or an authenticating reverse proxy, before opening it on a shared
network.

## Licence

AGPL-3.0, as the rest of Jeffrey. See [LICENSE](https://github.com/petrbouda/jeffrey/blob/master/LICENSE).
