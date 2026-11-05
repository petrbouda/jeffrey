---
name: write-report
tags: [routing, report, persons]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

I already went through the "persons dom" profile in Jeffrey: every GET /persons now runs an extra COUNT query before it looks the person up, which costs about 1.2 seconds over the run, and building the JSON tree makes the service allocate about a quarter more than before. Write that up as a short report for my team.
