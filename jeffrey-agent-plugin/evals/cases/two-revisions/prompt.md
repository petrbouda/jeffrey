---
name: two-revisions
tags: [routing, overlap, persons]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

Did my last commit make the persons service slower? Nothing has been profiled for it yet — measure HEAD against the commit before it, with the service in server/src/main/java/jeffrey/testapp/server.
