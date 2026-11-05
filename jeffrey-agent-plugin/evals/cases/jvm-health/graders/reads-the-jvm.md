---
# The JVM's own sections are read, not inferred from a flamegraph.
type: regex
target: mock_calls
pattern: '"tool"\s*:\s*"mcp__[^"]*__jvm_(?:gc|sections|system|container|threads|jit|configuration|safepoints|nativeMemory)"'
---
