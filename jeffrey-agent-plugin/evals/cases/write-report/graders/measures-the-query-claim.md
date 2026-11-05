---
# The evidence rule: every figure names the call that produced it in this session, so the user's account of
# the COUNT query is read back from the traces or the database dashboard before it is written down.
type: regex
target: mock_calls
pattern: '"tool"\s*:\s*"mcp__[^"]*__(?:traces_operationExport|traces_operations|jdbc_overview|jdbc_statementGroup|code_callSites)"'
---
