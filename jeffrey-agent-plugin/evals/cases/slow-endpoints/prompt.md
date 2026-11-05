---
name: slow-endpoints
tags: [routing, traces, latency, persons]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

Some requests to the persons service in the "persons dom" profile in Jeffrey are much slower than the rest. Which endpoints, and why?
