# Microscope plugin for Claude Code

Read JVM profiles from a running [Jeffrey Microscope](https://www.jeffrey-analyst.cafe/docs/microscope)
without leaving your terminal: point Claude at a `.jfr` file in your repository and it analyses it,
then lists the recordings you have analysed, queries their DuckDB tables, and pulls flamegraph, trace
and heap-dump exports straight into a Claude Code session in your own repository — so the profile and
the source code are in front of the same reader.

Every analysis tool is **read-only**. The one exception is `recordings_`, which creates profiles
rather than changing them, and which a Jeffrey can switch off with
`jeffrey.microscope.mcp.ingest.enabled=false`.

Full documentation: [Microscope MCP](https://www.jeffrey-analyst.cafe/docs/microscope-mcp).

## Install

Jeffrey's MCP server is **on by default** — a running Jeffrey is already serving it.
**Settings → Claude Code (MCP)** reports whether the endpoint is serving and shows the
connection details for this installation.

From Claude Code:

```
/plugin marketplace add petrbouda/jeffrey
/plugin install microscope@jeffrey
```

Or, working from a clone:

```bash
claude --plugin-dir ./jeffrey-claude-plugin
```

## Pointing it at your Jeffrey

The plugin ships with the endpoint set to `http://localhost:8585/api/internal/mcp`. Anywhere else — a
different port, a container, an SSH tunnel — change it in the plugin's own configuration; Claude Code
offers the field when you enable the plugin, and `/plugin` reopens it afterwards:

```
Jeffrey MCP endpoint: http://localhost:9000/api/internal/mcp
```

The setting lives in `~/.claude/settings.json`, so one machine can point at a tunnelled staging
Jeffrey while another stays on localhost. The Settings tab in Jeffrey shows the exact URL for your
installation.

## What you get

**Tools**, in fifteen families:

| Family | What it does |
|---|---|
| `profiles_` | The catalogue: which recordings are analysed, what each one can answer, and deep links into the UI |
| `flamegraph_` | Which graphs a profile supports, and the call tree as Markdown |
| `compare_` | Two profiles against each other: whether they are comparable, what moved, and the differential call tree |
| `traces_` | Trace operations, the application's own notifications, exemplars, span trees and span-scoped flamegraphs |
| `jvm_` | The machine underneath: garbage collection, safepoints, JIT compilation, threads, thread dumps, native memory, the container, the JVM flags and what it was started with |
| `http_` | The HTTP traffic the application served: percentiles, endpoints, status codes, slowest requests |
| `jdbc_` | Statement timings and groups, and the connection pool in front of them |
| `grpc_` | gRPC latency per service and method, and the message sizes moved |
| `methodtracing_` | Instrumented method timings (JEP 520): by cost, the worst invocations, the JVM's aggregates |
| `io_` | Socket and file I/O: bytes, targets and the slowest operations — the waiting a CPU graph cannot see |
| `blocking_` | Contended monitors, waits, parks and virtual-thread pinning |
| `timeline_` | When the samples landed: the busiest windows, and sub-second zoom inside one |
| `jfr_` | The profile's DuckDB tables — schema and read-only SQL |
| `heap_` | Heap summary, class histogram, dominator tree, leak suspects, GC-root paths, and read-only SQL |
| `recordings_` | The one that writes: imports a recording file and builds a profile from it |

`recordings_analyzeFile` takes an **absolute path**, and the file has to be on the machine Jeffrey
runs on — Jeffrey opens it, the client does not upload it. That is the usual case (one laptop running
both) and not the case for a Jeffrey in a container or on another host.

**Skills**, which you can also invoke directly:

- `/microscope:analyze-jfr` — where to start and which family answers which question
- `/microscope:analyze-heap` — a heap dump end to end: what is holding the memory, what is leaking,
  which class loader never went away, and the order the heap tools have to be run in
- `/microscope:compare-jfr` — before against after: whether a change made it slower, which methods
  moved, and whether the two recordings were comparable in the first place
- `/microscope:advise-jfr [profile-id | recording-file] [cpu|wall|alloc|lock]` — from a profile to a
  code change: the hottest CPU, wall-clock, allocation and blocking frames mapped to real source in
  your checkout, a recommendation, then the edit and a re-profile on request
- `/microscope:jfr-sql` — the JFR schema and the DuckDB idioms that go with it
- `/microscope:heap-sql` — the heap-dump index schema

The exports carry their own reading instructions, so the skills stay short: they cover the
workflows and the two schemas, not things the tool output already explains.

**One subagent**, `microscope:profile-analyst`. A single flamegraph export can run to 120,000
characters, and a question usually takes several. The analyst runs a sequence and returns only the
findings — the hot frames with their shares, or the retaining classes with their GC-root paths —
leaving everything it read in its own context. The skills hand it the reading and keep what needs
your conversation: mapping frames onto the checkout, the recommendation, and every question put to
you. It reads over MCP only, so it cannot touch your files, import a recording or propose an edit.

## Permissions

Claude Code asks before each tool the first time. Every Jeffrey tool except `recordings_` is
read-only, so approving the family once is usually what you want — either from the prompt, or up
front with `/permissions`:

```
mcp__plugin_microscope_jeffrey__*
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

## Security

The MCP endpoint has no authentication yet, exactly like the rest of Jeffrey's API: anyone who can
reach the address can read every profile in that installation. Keep Jeffrey bound to localhost, or
put it behind an SSH tunnel or an authenticating reverse proxy, before opening it on a shared
network.

## Licence

AGPL-3.0, as the rest of Jeffrey. See [LICENSE](https://github.com/petrbouda/jeffrey/blob/master/LICENSE).
