---
name: exception-inputs-counted-in-sql
tags: [sql, exceptions]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

The "checkout candidate" service in Jeffrey throws a lot of NumberFormatExceptions. Is that a handful of bad inputs thrown over and over, or a different input every time? Give me the numbers.
