---
# About 81 s blocked across 4 threads in a 30.6 s recording is summed thread-time: no share of the
# recording or of wall clock describes it (the report skill's denominator rule).
type: regex
pattern: '\d+(?:\.\d+)?\s*%\s*(?:of\s*)?(?:the\s*)?(?:wall|recording|elapsed|30(?:\.6)?\s*s)'
flags: i
match: not_contains
---
