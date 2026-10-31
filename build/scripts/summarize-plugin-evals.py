#!/usr/bin/env python3
#
# Jeffrey
# Copyright (C) 2026 Petr Bouda
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""Summarizes a `claude plugin eval` run of the microscope plugin as the table its baseline is kept in.

`claude plugin eval --json` reports each run's score, turns and cost, and — with `--keep-temp` — the
path of its trace. From the trace this adds what the score does not say: how many tool calls the
answer took, how many of them went to Jeffrey, and how many tokens went in and came out — the main
conversation's, and separately what the agents it delegated to read, since every agent writes its
own result line and the last one is the main conversation's. Those are the figures a restructure of
the skills is meant to move, so they are recorded beside the score.

Usage: summarize-plugin-evals.py <aggregate-result.json>
"""

import json
import statistics
import sys
from pathlib import Path

JEFFREY_TOOL_PREFIX = "mcp__plugin_microscope_jeffrey__"
NOT_A_CALL = {"ToolSearch"}


def trace_figures(trace: Path) -> dict | None:
    """Tool calls, Jeffrey calls, skills invoked and tokens, read from one run's trace."""
    if not trace.exists():
        return None
    calls = jeffrey = 0
    skills = []
    results = []
    for line in trace.read_text().splitlines():
        event = json.loads(line)
        if event.get("type") == "assistant":
            for block in event["message"].get("content", []):
                if block.get("type") != "tool_use" or block["name"] in NOT_A_CALL:
                    continue
                calls += 1
                jeffrey += block["name"].startswith(JEFFREY_TOOL_PREFIX)
                if block["name"] == "Skill":
                    skills.append(block["input"].get("skill", "?"))
        if "total_cost_usd" in event and "usage" in event:
            results.append(event["usage"])
    if not results:
        return None
    *agents, main = results
    return {"calls": calls, "jeffrey": jeffrey, "skills": skills, "in": tokens_read(main),
            "out": main.get("output_tokens", 0), "agents_in": sum(map(tokens_read, agents))}


def tokens_read(usage: dict) -> int:
    return sum(usage.get(key, 0) for key in ("input_tokens", "cache_creation_input_tokens", "cache_read_input_tokens"))


def median(values: list) -> str:
    present = [value for value in values if value is not None]
    if not present:
        return "—"
    middle = statistics.median(present)
    return f"{middle:,.0f}" if middle >= 10 else f"{middle:.2f}".rstrip("0").rstrip(".")


def main() -> int:
    result = json.loads(Path(sys.argv[1]).read_text())
    print("| Case | Score | Runs | Turns | Tool calls | Jeffrey calls | Tokens in | Agents' tokens in | Tokens out "
          "| Cost (USD) | Skills invoked |")
    print("|---|---|---|---|---|---|---|---|---|---|---|")
    for case in result["cases"]:
        runs = case["arms"]["with"]
        figures = [trace_figures(Path(run["tracePath"])) if run.get("tracePath") else None for run in runs]
        figures = [figure for figure in figures if figure]
        skills = sorted({skill for figure in figures for skill in figure["skills"]})
        print(f"| {case['name']} | {case['aggregates']['score']:.2f} | {len(runs)} "
              f"| {median([run.get('turns') for run in runs])} "
              f"| {median([f['calls'] for f in figures])} | {median([f['jeffrey'] for f in figures])} "
              f"| {median([f['in'] for f in figures])} | {median([f['agents_in'] for f in figures])} "
              f"| {median([f['out'] for f in figures])} "
              f"| {median([run.get('costUsd') for run in runs])} | {', '.join(skills) or '—'} |")
    aggregates = result["aggregates"]
    print(f"\nOverall score {aggregates['overallScore']:.2f}, {aggregates['casesPassed']}/{aggregates['casesTotal']} "
          f"cases at the threshold; {result.get('costUsd', 0):.2f} USD including the judge; "
          f"Claude Code {result.get('claudeVersion')}. Medians per case.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
