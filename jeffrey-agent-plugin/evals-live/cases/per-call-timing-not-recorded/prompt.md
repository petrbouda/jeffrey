---
name: per-call-timing-not-recorded
tags: [routing, latency, persons]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

How long does PersonRepository.count take per call in the "persons dom" profile in Jeffrey? This is the persons service's repository.
