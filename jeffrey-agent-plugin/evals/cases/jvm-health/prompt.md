---
name: jvm-health
tags: [routing, jvm, persons]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

Before we scale the persons service up: is the JVM itself healthy? Jeffrey has the "persons dom" profile.
