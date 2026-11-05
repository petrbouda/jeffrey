---
# The change is read from the repository's history, not assumed from the prompt. Which commit it names is
# the judge's to check (honest-movement-at-the-profiled-commits): HEAD here is a README commit one past the
# candidate's, and a reply that takes it for the change fails there. A run that finds the pricing commit
# in `git log` and shows it as HEAD~1 has read the right change; the hash need not be typed.
type: tool_used
tool: Bash
input_match: '"command"\s*:\s*"git (?:diff|show|log)'
min: 1
---
