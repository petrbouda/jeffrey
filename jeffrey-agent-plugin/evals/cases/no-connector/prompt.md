---
name: no-connector
tags: [routing, connector, persons]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

Why was our persons service slow in production this morning? Its recordings should be on the connector.
