---
name: did-it-regress
tags: [routing, overlap, persons]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

Did my last change make the persons service slower? Jeffrey has "persons direct" from before the change and "persons dom" from after it.
