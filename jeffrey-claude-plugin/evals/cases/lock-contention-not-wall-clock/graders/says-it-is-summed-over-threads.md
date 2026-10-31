---
# Blocked time is summed over the threads that waited, so the answer says so (and not as a share of wall clock):
# as a sum, or as how many threads were blocked at any moment, or what each thread spent.
type: regex
pattern: '(?:over|across|of|all|between|by|on)\s+(?:the\s+|all\s+|its\s+)*(?:4|four|several|multiple|worker|blocked|waiting|contending)?\s*(?:worker\s+)?threads|summed|sum of|thread[- ]?(?:time|seconds)|per[- ]thread|combined (?:time|wait)|cumulative|aggregate|add(?:s|ed)? up|in total across|threads? (?:were |was )?blocked at any (?:one )?moment|at any (?:one )?moment|each (?:of the \w+ )?(?:thread|worker)s? (?:spent|waited|was blocked)|each spent'
flags: i
---
