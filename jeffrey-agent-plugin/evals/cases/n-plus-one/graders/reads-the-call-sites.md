---
# Which line issues a statement, and how often one request does: the call sites, counted per trace —
# not the database dashboard's totals, which cannot tell one request's repeats from many requests.
type: tool_used
tool: mcp__plugin_microscope_jeffrey__code_callSites
input_match: '"eventType"\s*:\s*"jeffrey\.JdbcQuery"'
min: 1
---
