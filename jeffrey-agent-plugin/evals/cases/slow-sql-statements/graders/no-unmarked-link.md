---
# A link without ↗ renders as plain words in a terminal, its URL hidden: every link is marked.
type: regex
pattern: '\[[^\]↗\n]*\]\(<?https?://localhost'
match: not_contains
---
