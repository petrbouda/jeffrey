---
# Sample counts are not an end-to-end slowdown: no overall "N% slower" stated as measured.
type: regex
pattern: '\b(?:the )?(?:application|service|checkout|request|run|program|overall|end[- ]to[- ]end|candidate)\b[^.\n]{0,40}\b(?:is|was|got|became|runs?)\s+\d+(?:\.\d+)?\s*%\s+slower|slowed down by\s+\d+(?:\.\d+)?\s*%|\d+(?:\.\d+)?\s*%\s+(?:overall|end[- ]to[- ]end)\s+(?:slow|regress)'
flags: i
match: not_contains
---
