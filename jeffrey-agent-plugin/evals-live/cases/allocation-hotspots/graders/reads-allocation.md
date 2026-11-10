---
# The allocation itself is read off Jeffrey — the allocation overview, the own code charged on the allocation
# event, or its flamegraph — not guessed from the source.
type: regex
target: trace
pattern: '"name"\s*:\s*"mcp__[^"]*__(?:memory_allocations|code_hotspots|flamegraph_export|code_method)"'
---
