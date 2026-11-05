---
name: count-queries
tags: [routing, jdbc, traces, persons]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

Our database runs far more COUNT queries than the persons service gets count requests. In Jeffrey's "persons dom" profile, where do the extra ones come from?
