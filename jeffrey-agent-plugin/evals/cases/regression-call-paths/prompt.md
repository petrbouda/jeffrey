---
name: regression-call-paths
tags: [routing, compare, persons]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep]
---

In Jeffrey, "persons direct" is the persons service's build before a change and "persons dom" the build after it, and the new one uses more CPU. Through which call path did the extra CPU time come?
