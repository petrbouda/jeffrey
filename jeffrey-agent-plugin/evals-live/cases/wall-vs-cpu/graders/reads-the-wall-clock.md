---
# Waiting is read off the wall-clock samples, not the CPU ones.
type: regex
target: trace
pattern: '"name"\s*:\s*"mcp__[^"]*__(?:flamegraph_export|code_hotspots|timeline_hotWindows|compare_movements)"\s*,\s*"input"\s*:\s*\{[^}]*WallClock'
---
