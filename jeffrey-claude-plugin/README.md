# Microscope plugin for Claude Code

Read JVM profiles from a running [Jeffrey Microscope](https://www.jeffrey-analyst.cafe/docs/microscope)
without leaving your terminal: list the recordings you have analysed, query their DuckDB tables, and
pull flamegraph, trace and heap-dump exports straight into a Claude Code session in your own
repository — so the profile and the source code are in front of the same reader.

Everything the plugin exposes is **read-only**.

Full documentation: [Microscope MCP](https://www.jeffrey-analyst.cafe/docs/microscope-mcp).

## Install

Jeffrey's MCP server is **off by default**. Turn it on first:

> **Settings → Claude Code (MCP) → enable**

Then, from Claude Code:

```
/plugin marketplace add petrbouda/jeffrey
/plugin install microscope@jeffrey
```

Or, working from a clone:

```bash
claude --plugin-dir ./jeffrey-claude-plugin
```

## Pointing it at your Jeffrey

The plugin defaults to `http://localhost:8080/api/internal/mcp`. Anywhere else — a different port, a
container, an SSH tunnel — set the endpoint before starting Claude Code:

```bash
export JEFFREY_MCP_URL="http://localhost:9000/api/internal/mcp"
```

The Settings tab shows the exact URL for your installation.

## What you get

**Tools**, in five families:

| Family | What it reads |
|---|---|
| `profiles_` | The catalogue: which recordings are analysed, what each one can answer, a deep link into the UI |
| `flamegraph_` | Which graphs a profile supports, and the call tree as Markdown |
| `traces_` | Trace operations, exemplars, span trees and span-scoped flamegraphs |
| `jfr_` | The profile's DuckDB tables — schema and read-only SQL |
| `heap_` | Heap summary, class histogram, dominator tree, leak suspects, GC-root paths, and read-only SQL |

**Skills**, which you can also invoke directly:

- `/microscope:analyze-profile` — where to start and which family answers which question
- `/microscope:jfr-sql` — the JFR schema and the DuckDB idioms that go with it
- `/microscope:heap-sql` — the heap-dump index schema

The exports carry their own reading instructions, so the skills stay short: they cover the workflow
and the two schemas, not things the tool output already explains.

## Permissions

Claude Code asks before each tool the first time. Since every Jeffrey tool is read-only, approving
the family once is usually what you want — either from the prompt, or up front with `/permissions`:

```
mcp__plugin_microscope_jeffrey__*
```

## Try it

With a profile analysed in Jeffrey:

> list the Jeffrey profiles, then show me where the CPU time goes in the most recent one

> the `GET /api/orders` operation is slow — find a slow example and tell me what the JVM was doing
> inside its slowest span

## Security

The MCP endpoint has no authentication yet, exactly like the rest of Jeffrey's API: anyone who can
reach the address can read every profile in that installation. Keep Jeffrey bound to localhost, or
put it behind an SSH tunnel or an authenticating reverse proxy, before opening it on a shared
network.

## Licence

AGPL-3.0, as the rest of Jeffrey. See [LICENSE](../LICENSE).
