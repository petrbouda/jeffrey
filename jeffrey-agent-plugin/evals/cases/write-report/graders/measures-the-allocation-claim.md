---
# Likewise the quarter more allocation: read off the allocation samples or the allocation dashboard (4.1 GB
# against 3.2 GB in TLAB allocations) before it is reported.
type: regex
target: mock_calls
pattern: '"tool"\s*:\s*"mcp__[^"]*__(?:memory_allocations|compare_movements|compare_flamegraph|flamegraph_export|code_hotspots)"\s*,\s*"input"\s*:\s*\{[^\n]*(?:ObjectAllocation|profileId)'
---
