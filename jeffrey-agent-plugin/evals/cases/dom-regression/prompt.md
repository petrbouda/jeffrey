---
name: dom-regression
tags: [routing, compare, persons]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

We rolled out a new build of the persons service. In Jeffrey, "persons direct" is the build before and "persons dom" is the new one. Did the new build make the service slower, and where?
