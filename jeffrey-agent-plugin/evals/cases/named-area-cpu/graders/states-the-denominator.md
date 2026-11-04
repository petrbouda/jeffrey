---
# The answer states what its shares are of: the export's total sample count, whatever the recording,
# its event type named or not ("3,118 `jdk.ExecutionSample` samples").
type: regex
pattern: '\d[\d,]{2,}\s*(?:on-CPU\s+|CPU\s+|execution\s+|`[\w.$]+`\s+)?samples'
flags: i
---
