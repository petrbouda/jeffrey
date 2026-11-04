---
# Both profiles were imported at their commits (liveCommits), and HEAD is one commit past the
# candidate's: the change to read is baseline..candidate, named by those commits — not HEAD~1..HEAD,
# which here is a README.
type: tool_used
tool: Bash
input_match: '"command"\s*:\s*"git (?:diff|show|log)[^"]*(?:1cc032a|c71aeee)'
min: 1
---
