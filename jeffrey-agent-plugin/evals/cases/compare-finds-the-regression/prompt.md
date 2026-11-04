---
name: compare-finds-the-regression
tags: [routing, compare]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

I changed the pricing code. In Jeffrey, "checkout baseline" is before the change and "checkout candidate" is after. Did the change make CPU usage worse, and where?
