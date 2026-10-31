---
type: llm
focus: trace
---

PASS if the agent put Jeffrey's menu of investigation areas to the user — with AskUserQuestion or
as a table or list — and then stopped to let the user choose, mentioning the leads the summary
already flags (lock contention, a Full GC). FAIL if it ran analyses nobody chose, such as
exporting a flamegraph, or presented conclusions without offering the choice.
