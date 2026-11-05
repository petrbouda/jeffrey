---
# The evidence rule: every figure names the call that produced it in this session, so the user's
# account of the CPU is read back before it is written down — from the CPU flamegraph, or from the CPU
# charged to each method (code_hotspots), which answers "most of the CPU goes to this method" as directly.
type: regex
target: mock_calls
pattern: '"tool"\s*:\s*"mcp__[^"]*__(?:flamegraph_export|code_hotspots)"\s*,\s*"input"\s*:\s*\{[^\n]*"eventType"\s*:\s*"jdk\.ExecutionSample"'
---
