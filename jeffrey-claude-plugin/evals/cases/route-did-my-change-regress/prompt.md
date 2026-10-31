---
name: route-did-my-change-regress
tags: [routing, overlap]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

Did my last change to the pricing code make checkout slower? Jeffrey has "checkout baseline" from before the change and "checkout candidate" from after it.
