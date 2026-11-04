---
name: orders-by-coupon-in-sql
tags: [sql, heap]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

In the "checkout heap dump" profile in Jeffrey, the audit log keeps every order it records alive. How many of those orders carry a SPRING- promo coupon rather than a plain numeric one?
