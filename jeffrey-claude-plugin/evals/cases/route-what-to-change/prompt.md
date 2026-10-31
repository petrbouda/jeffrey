---
name: route-what-to-change
tags: [routing, overlap]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

Based on the "checkout candidate" profile in Jeffrey, what should I change in the code to make checkout faster? The code is in the com.acme.checkout package; it is not checked out here.
