---
# Allocation is ranked by bytes: the answer carries byte figures or says its shares are of bytes.
type: regex
pattern: '(?:of|by|in)\s+(?:the\s+)?(?:total\s+|all\s+|allocated\s+|sampled\s+)*bytes|\d+(?:\.\d+)?\s*(?:[KMGT]i?B)\b'
flags: i
---
