---
# Aggregate in SQL rather than pulling rows back to count them.
type: tool_used
tool: mcp__plugin_microscope_jeffrey__jfr_executeQuery
input_match: '(?:COUNT|count)\s*\(\s*(?:DISTINCT|distinct)|(?:GROUP|group)\s+(?:BY|by)'
min: 1
---
