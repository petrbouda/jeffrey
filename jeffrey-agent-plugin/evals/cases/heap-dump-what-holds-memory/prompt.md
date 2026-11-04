---
name: heap-dump-what-holds-memory
tags: [routing, heap]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

Analyze /tmp/jeffrey-eval/checkout.hprof in Jeffrey. What is holding the memory, and is anything leaking?
