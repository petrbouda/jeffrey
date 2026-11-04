---
name: slow-requests-from-traces
tags: [routing, traces, latency]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

Some POST /checkout requests in the "checkout candidate" profile in Jeffrey are much slower than the rest. Which ones, and why?
