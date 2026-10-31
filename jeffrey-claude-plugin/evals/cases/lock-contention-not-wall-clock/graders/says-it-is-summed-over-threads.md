---
# Blocked time is summed over the threads that waited, so the answer says so (and not as a share of wall clock).
type: regex
pattern: '(?:over|across|of|all|between|by|on)\s+(?:the\s+|all\s+|its\s+)*(?:4|four|several|multiple|worker|blocked|waiting|contending)?\s*(?:worker\s+)?threads|summed|sum of|thread[- ]?(?:time|seconds)|per[- ]thread|combined (?:time|wait)|cumulative|aggregate|add(?:s|ed)? up|in total across'
flags: i
---
