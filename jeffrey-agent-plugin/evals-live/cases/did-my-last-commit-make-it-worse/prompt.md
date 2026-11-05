---
name: did-my-last-commit-make-it-worse
tags: [code, compare, provenance, checkout]
max_turns: 25
timeout_seconds: 600
runs: 3
allowed_tools: [Skill, AskUserQuestion, Agent, Read, Glob, Grep, "Bash(git rev-parse *)", "Bash(git diff *)", "Bash(git log *)", "Bash(git show *)"]
---

Did my last change to the pricing code make checkout more expensive? In Jeffrey, "checkout baseline" is from before it and "checkout candidate" from after it.
