---
name: route-write-it-up
tags: [routing, report]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

I already went through the "checkout candidate" profile in Jeffrey: most of the CPU goes to PriceCalculator.formatLine, and the SessionCache lock blocks the workers for about 81 seconds. Write that up as a short report for my team.
