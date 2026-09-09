# Microscope plugin

Read JVM profiles from a running [Jeffrey Microscope](https://www.jeffrey-analyst.cafe/docs/microscope)
without leaving your terminal: point your coding agent at a `.jfr` file in your repository and it
analyses it, then lists the recordings you have analysed, queries their DuckDB tables, and pulls
flamegraph, trace and heap-dump exports straight into a session in your own repository — so the
profile and the source code are in front of the same reader.

One package, three plugin formats. **Claude Code** reads `.claude-plugin/plugin.json`; **Codex** and
the other [Agent Plugins](https://agent-plugins.org/) clients read the root `plugin.json` and
`mcp.json`; **Gemini CLI** reads `gemini-extension.json`. The skills and the MCP server underneath
are the same files for all three.

Every analysis tool is **read-only**, and every tool says so in its MCP annotations rather than
leaving a client to infer it. Six do not read: `recordings_analyzeFile` and `recordings_analyzeRecording`, which create profiles
rather than changing them, `heap_prepare`, which writes only a cache, `hubs_download`, which pulls one
off another machine, and `ide_link` and `ide_open`, which act on the editor running beside Jeffrey
rather than on any profile. Each declares itself, so the reading members of those same families -
`recordings_list`, `recordings_status`, `heap_status` - are not swept up with them. Two families
reach outside this server and have switches of their own —
`jeffrey.microscope.mcp.hubs.enabled=false` for the one that leaves the machine, and
`jeffrey.microscope.mcp.ide.enabled=false` for the one that reaches into the developer's IntelliJ.

Full documentation: [Microscope MCP](https://www.jeffrey-analyst.cafe/docs/microscope-mcp) —
[Claude Code](https://www.jeffrey-analyst.cafe/docs/microscope-mcp/claude-code),
[Codex](https://www.jeffrey-analyst.cafe/docs/microscope-mcp/codex),
[Gemini CLI](https://www.jeffrey-analyst.cafe/docs/microscope-mcp/gemini),
[other clients](https://www.jeffrey-analyst.cafe/docs/microscope-mcp/other-clients).

## Install

Jeffrey's MCP server is **on by default** — a running Jeffrey is already serving it, at
`/api/mcp` on whatever address and port you reach Jeffrey on (`http://localhost:8585` unless
you changed `server.port`).

That endpoint used to be `/api/internal/mcp`, and still answers there, so a client configured before
the move keeps working. A Microscope older than the move serves *only* the old path — point the
plugin at it, or upgrade Jeffrey.

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

**Gemini CLI:**

```bash
git clone https://github.com/petrbouda/jeffrey
gemini extensions install ./jeffrey/jeffrey-claude-plugin
```

Gemini installs an extension from a directory holding a `gemini-extension.json`, which is this
directory in a clone. The GitHub-URL form does not work here: Gemini looks for the manifest at the
root of what it clones, and in this repository it lives one directory down. `/extensions` lists it,
`/mcp` says whether it reached Jeffrey.

Any of them can also skip the plugin and register the endpoint by hand — the docs above cover
`claude mcp add`, `codex mcp add`, Gemini's `mcpServers` block, and the raw JSON-RPC for anything
else.

## Pointing it at your Jeffrey

The plugin ships with the endpoint set to `http://localhost:8585/api/mcp`.

In **Claude Code** anywhere else — a different port, a container, an SSH tunnel — is a setting:
Claude Code offers the field when you enable the plugin, `/plugin` reopens it afterwards, and the
value lives per machine in `~/.claude/settings.json`.

```
Jeffrey MCP endpoint: http://localhost:9000/api/mcp
```

**Codex has no equivalent.** The Agent Plugins format forbids placeholder expansion in a server URL,
so a plugin-provided endpoint is fixed at `localhost:8585`. For any other address, disable the
plugin's server and register your own in `~/.codex/config.toml`:

```toml
[mcp_servers.jeffrey]
url = "http://localhost:9000/api/mcp"
```

The skills keep working; only the server registration moves.

**Gemini CLI has a setting, like Claude Code.** The extension declares one, and Gemini asks for it
while installing; the manifest reads `${JEFFREY_MCP_ENDPOINT:-http://localhost:8585/api/mcp}`, so the
default stands until you give it something. `gemini extensions install` with `--skip-settings` skips
the question, and exporting `JEFFREY_MCP_ENDPOINT` answers it from the environment instead — the same
variable the session-start check reads, so both agree about where Jeffrey is.

Registering the server yourself works too, in `~/.gemini/settings.json` for every project or a
checkout's `.gemini/settings.json` for one:

```json
{
  "mcpServers": {
    "jeffrey": {
      "httpUrl": "http://localhost:9000/api/mcp"
    }
  }
}
```

`httpUrl` is Gemini's key for streamable HTTP; `url` there means SSE, which this server does not
speak. Keep the name `jeffrey`: the skills name tools by the part after the prefix, and the prefix is
built from the server's name.

## What you get

**Tools**, in eighteen families:

| Family | What it does |
|---|---|
| `profiles_` | The catalogue: which recordings are analysed, what each one can answer, and deep links into the UI |
| `flamegraph_` | Which graphs a profile supports, and the call tree as Markdown |
| `compare_` | Two profiles against each other: whether they are comparable, what moved, and the differential call tree |
| `traces_` | Trace operations, the application's own notifications, exemplars, span trees, span-scoped flamegraphs, and the attributes that say which population a trace belonged to |
| `jvm_` | The machine underneath: garbage collection and the pages beneath it, safepoints, JIT compilation, threads, thread dumps, native memory, class loading, exceptions, the host and who else is on it, TLS and certificates, the container, the JVM flags and what it was started with |
| `http_` | The HTTP traffic the application served: percentiles, endpoints, status codes, slowest requests |
| `jdbc_` | Statement timings and groups, and the connection pool in front of them |
| `grpc_` | gRPC latency per service and method, and the message sizes moved |
| `methodtracing_` | Instrumented method timings (JEP 520): by cost, the worst invocations, the JVM's aggregates |
| `io_` | Socket and file I/O: bytes, targets and the slowest operations — the waiting a CPU graph cannot see |
| `blocking_` | Contended monitors, waits, parks and virtual-thread pinning |
| `timeline_` | When the samples landed: the busiest windows, and sub-second zoom inside one |
| `memory_` | Allocation by type, and JFR-side leak candidates that need no heap dump |
| `jfr_` | The profile's DuckDB tables — schema, the fields of one event type, and read-only SQL |
| `heap_` | Heap summary, class histogram, dominator tree, leak suspects, GC-root paths, a two-dump diff, read-only SQL, OQL, and the one pair that builds rather than reads: `heap_prepare` and `heap_status` |
| `recordings_` | One of those that write: imports a recording file and builds a profile from it |
| `hubs_` | The recordings still on a connected Jeffrey Hub: lists sessions across every hub and pulls one in |
| `ide_` | Where a frame actually lives, answered by the developer's running IntelliJ: the file and line for a class and method, a class's source, which checkouts are open and on what commit, and — the one tool here with a visible side effect — opening a location in the editor |

`recordings_analyzeFile` takes an **absolute path**, and the file has to be on the machine Jeffrey
runs on — Jeffrey opens it, the client does not upload it. That is the usual case (one laptop running
both) and not the case for a Jeffrey in a container or on another host.

**Skills**, which the agent picks up on its own and you can also invoke directly —
`/microscope:analyze-jfr` in Claude Code, `$analyze-jfr` in Codex, and by name in Gemini CLI, which
loads a skill when the question calls for it:

- `analyze-jfr` — where to start and which family answers which question
- `profile-run` — a workload that has not been recorded yet: what to run it under, for how long, and
  where the file has to land
- `analyze-heap` — a heap dump end to end: what is holding the memory, what is leaking,
  which class loader never went away, and the order the heap tools have to be run in
- `analyze-hub` — the recordings that never reached this machine: finds a session across the
  connected Jeffrey Hubs, pulls it in, and hands off to `analyze-jfr` or `analyze-heap`
- `compare-jfr` — before against after: whether a change made it slower, which methods
  moved, and whether the two recordings were comparable in the first place
- `regression-check` — the same question starting from two revisions rather than two profiles: build
  and record both, then weigh them
- `advise-jfr` — from a profile to a code change: the hottest CPU, wall-clock, allocation and
  blocking frames mapped to real source in your checkout, a recommendation, then the edit and a
  re-profile on request
- `jfr-sql` — the JFR schema and the DuckDB idioms that go with it
- `heap-sql` — the heap-dump index schema
- `report` — the shape every finding is written in, and the evidence rules behind it: every figure
  names its tool call, shares say what they are a share of, sampled and rule-based evidence is
  capped at medium confidence, and what the recording could not answer is reported apart from the
  findings

The exports carry their own reading instructions, so the skills stay short: they cover the
workflows and the two schemas, not things the tool output already explains.

**Three agents.** `profile-analyst` is the general one. A single flamegraph export can run to 120,000 characters,
and a question usually takes several. The analyst runs a sequence and returns only the findings — the
hot frames with their shares, or the retaining classes with their GC-root paths — leaving everything
it read in its own context. The skills hand it the reading and keep what needs your conversation:
mapping frames onto the checkout, the recommendation, and every question put to you.

`heap-triage` is the heap specialist: it carries `analyze-heap` and `heap-sql`, runs the whole leak
route, and will build a missing dominator tree or report with `heap_prepare` rather than reporting an
empty result.

`profile-lead` is for the open-ended question — "why is this service slow", "review this recording" —
where nobody has said which dimension to look at. It triages from `profiles_summary` itself, reads
the capability gaps before anything else, dispatches `profile-analyst` and `heap-triage` only for
the dimensions the summary justifies and all at once, then merges what comes back: findings carry a
stable id, so the same condition reported twice collapses into one, ranked by share of wall clock,
of samples or of the heap. It holds the orientation tools and the two specialists, and no export
tool of its own.

Claude Code gets them from the plugin as `microscope:profile-analyst`, `microscope:heap-triage` and
`microscope:profile-lead`, restricted to the reading tools so they cannot touch your files, import
a recording, pull one off a hub, move your editor or propose an edit. **Agent Plugins defines only
skills and MCP servers**, so Codex cannot receive an agent from a plugin — copy
[`codex/agents/profile-analyst.toml`](codex/agents/profile-analyst.toml),
[`codex/agents/heap-triage.toml`](codex/agents/heap-triage.toml) and
[`codex/agents/profile-lead.toml`](codex/agents/profile-lead.toml) to `~/.codex/agents/` instead.
Those versions are sandboxed read-only, but their tool restriction is instruction-level rather than
enforced.

**Gemini CLI gets two of the three, also as files to copy** —
[`gemini/agents/profile-analyst.md`](gemini/agents/profile-analyst.md) and
[`gemini/agents/heap-triage.md`](gemini/agents/heap-triage.md) to `~/.gemini/agents/` for every
repository, or `.gemini/agents/` for one; `/agents` then lists them. The extension cannot carry them
even though Gemini reads an extension's `agents/`: it validates that frontmatter strictly and rejects
any key it does not define, and the plugin's own agents carry Claude Code's `disallowedTools`,
`skills` and `color`. There is no `profile-lead` for Gemini at all — a Gemini subagent may not
dispatch another subagent, and dispatching the other two is that agent's whole job — so lead an
open-ended investigation from the main conversation there.

**Prompts and resources.** The server also serves the ten skills over the protocol itself, as MCP
prompts — the same files the plugin ships, copied onto the server's classpath when it is built, so
the two cannot drift. A client that cannot install a plugin (Cursor, VS Code, Kiro, anything
registered by hand) gets them through `prompts/list` and `prompts/get` rather than being left with
the tools and no account of how to use them. `resources/list` and `resources/read` expose the profile
catalogue the same way, with URI templates for one profile's summary
(`jeffrey://profile/{profileId}/summary`) and a flamegraph of one event type
(`jeffrey://profile/{profileId}/flamegraph/{eventType}`) — a client can pin one into the conversation
instead of spending a tool call on it.

**Narrowing what is advertised.** `jeffrey.microscope.mcp.families` takes a comma-separated
allow-list of family prefixes — `profiles,flamegraph,jfr,heap`, say — and everything outside it is
left out of `tools/list` entirely. Empty, the default, advertises all of them. It is the blunt
instrument for a shared installation, and it composes with the three per-family switches above.

## Permissions

Every client asks before each tool the first time. Every Jeffrey tool reads except the five named
above, so approving a family once is usually what you want — `hubs_` and `ide_` are the two worth
reading twice, since one moves data off another machine and the other acts on your editor.

Claude Code, from the prompt or up front with `/permissions`:

```
mcp__plugin_microscope_jeffrey__*
```

Codex, in `~/.codex/config.toml`:

```toml
[mcp_servers.jeffrey]
default_tools_approval_mode = "auto"
```

Gemini CLI keeps the same choice behind its prompt — *Always allow this tool* and *Always allow this
server* build the allow-list as you go. To decide up front instead, mark the server trusted in
`settings.json`, or name the tools you want without a prompt:

```json
{
  "mcpServers": {
    "jeffrey": {
      "httpUrl": "http://localhost:8585/api/mcp",
      "trust": true
    }
  }
}
```

`trust` covers every tool on the server, `ide_` and `hubs_download` included, so it is the blunt
instrument; `excludeTools` on the same entry is how you keep those two out of reach entirely.

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
├── gemini-extension.json     Gemini CLI extension manifest — the same server, skills and agents
├── hooks/                    SessionStart check — is Jeffrey actually serving (Claude Code and Gemini)
├── skills/                   Ten skills, read by all three formats
├── agents/
│   ├── profile-analyst.md    Claude Code subagent
│   ├── heap-triage.md        …the heap specialist
│   └── profile-lead.md       …and the lead that triages, dispatches the two and merges their findings
├── codex/agents/
│   ├── profile-analyst.toml  The same agents as Codex custom agents, installed by hand
│   ├── heap-triage.toml
│   └── profile-lead.toml
└── gemini/agents/
    ├── profile-analyst.md    Two of them as Gemini CLI subagents, also installed by hand
    └── heap-triage.md
```

The directory keeps its `jeffrey-claude-plugin` name so existing installs and marketplace entries
keep resolving; it serves all three clients.

**One hook, two clients.** Both read `hooks/hooks.json` in the same shape and both spell a hook's
output the same way, but they name the plugin's own directory differently — `CLAUDE_PLUGIN_ROOT` in
the environment, `${extensionPath}` substituted into the command — so the command resolves whichever
is there and exits silently when neither is, rather than reporting a missing script. It carries no
`timeout` on purpose: Claude Code reads that field as seconds and Gemini as milliseconds, and the
probe bounds itself at three seconds anyway.

**Three agent directories, one set of agents.** `agents/` is Claude Code's, and the only one a plugin
carries; `codex/` and `gemini/` hold the same analyst and heap specialist in the dialect each of those
clients validates, to be copied by hand. Gemini does read an installed extension's `agents/`, and logs
a load error for each of the three Claude files, whose frontmatter its strict schema rejects — the
errors are debug-level, and nothing else follows from them.

In both hand-copied sets the read-only promise rests on the *No writing* rule in the agent's own
instructions rather than on a deny-list the client does not have.

## Security

The endpoint refuses a request carrying an `Origin` header it did not serve, which is the check the
MCP specification asks of a local HTTP server — a CLI client sends no `Origin`, so it costs nothing
here and closes the path where a page in your browser drives the server.

Beyond that it is unauthenticated, exactly like the rest of Jeffrey's API: anyone who can reach the
address can read every profile in that installation, and Jeffrey has nothing of its own to turn on.
Keep it bound to localhost with `server.address=127.0.0.1`, or put it behind an SSH tunnel or an
authenticating reverse proxy.

## Licence

AGPL-3.0, as the rest of Jeffrey. See [LICENSE](https://github.com/petrbouda/jeffrey/blob/master/LICENSE).
