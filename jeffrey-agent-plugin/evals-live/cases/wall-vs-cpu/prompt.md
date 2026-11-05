---
name: wall-vs-cpu
tags: [routing, waiting, persons]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

Requests to the persons service feel slow, but the CPU looks low. Where do the request threads actually spend their time in the "persons dom" profile in Jeffrey?
