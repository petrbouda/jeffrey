---
name: lock-contention-not-wall-clock
tags: [routing, waiting]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

Which locks hold the threads back in the "checkout candidate" profile in Jeffrey, and how badly?
