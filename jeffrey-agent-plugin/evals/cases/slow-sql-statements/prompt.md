---
name: slow-sql-statements
tags: [routing, jdbc, persons]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

Which SQL statements cost the persons service the most time? The profile is "persons dom" in Jeffrey.
