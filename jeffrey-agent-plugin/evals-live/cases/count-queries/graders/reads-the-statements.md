---
# The statements are read off the database dashboard, their call sites or the traces — not guessed.
type: regex
target: trace
pattern: '"name"\s*:\s*"mcp__[^"]*__(?:jdbc_overview|jdbc_statementGroup|code_callSites|traces_operationExport|code_runtimeSurface)"'
---
