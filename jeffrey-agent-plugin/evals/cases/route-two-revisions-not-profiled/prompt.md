---
name: route-two-revisions-not-profiled
tags: [routing, overlap]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

Did my last commit make our checkout benchmark slower? Nothing has been profiled for it yet — measure HEAD against the commit before it, with the benchmark in com/acme/checkout/Checkout.java.
