# Tool-name prefixes

The skills name Jeffrey's tools without the prefix a client puts in front of them — `profiles_summary`,
`flamegraph_export`, `heap_getLeakSuspects`. What that prefix is depends on the host:

| Host | Prefix | Example |
|---|---|---|
| Claude Code, with the `microscope` plugin | `mcp__plugin_microscope_jeffrey__` | `mcp__plugin_microscope_jeffrey__profiles_summary` |
| Codex, and any server registered by hand under the name `jeffrey` | `mcp__jeffrey__` | `mcp__jeffrey__profiles_summary` |
| Gemini CLI, which spells it with single underscores | `mcp_jeffrey_` | `mcp_jeffrey_profiles_summary` |

The part after the prefix is exact and camelCase: `jfr_listTables`, not `jfr_list_tables`.
