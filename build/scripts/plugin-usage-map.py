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
"""Maps the microscope plugin's skills, agents and tools, and how recorded eval runs used them.

Two sources go into one JSON:
- the wiring as the plugin describes it: the tools each skill's instructions name (its SKILL.md and
  `references/`, read with `check-plugin-references.py`'s own parser), the skills and agents it hands
  over to, each agent's allowed and withheld tools, and every tool of the `tools/list` snapshot;
- the wiring as the runs used it, from the stream-json traces `claude plugin eval --keep-temp` leaves
  behind (each run's `tracePath` in `evals{,-live}/results/*/aggregate-result.json`). Every Skill,
  Agent and tool call becomes an ordered step in its lane: the main conversation, or the agent whose
  call spawned it. Each Jeffrey call is charged to the skill active in its lane, or to that agent.

The JSON keeps every run's totals and, for each eval case, the latest results' runs step by step. It
is written into jeffrey-pages, whose Agent Plugin page (`/agent-plugin`) replays the runs over a map
of the wiring. Traces live in temporary directories, so a run whose trace is gone is skipped.
Full generation requires `--recorded-version`: every ingested trace must come from that plugin
version. Trace metadata does not identify it, so the current manifest cannot supply it.

`--wiring-only` needs no traces: it refreshes the documented wiring and its `wiringPlugin` version
at `--out`, keeping the recorded `plugin` version and runs. `agentAliases` links historical agent
names to current wiring without changing the recorded names. `test-plugin-usage-map.py` fails until
the wiring is refreshed after a skill, agent or tool change.

Usage:
    python3 build/scripts/plugin-usage-map.py --recorded-version VERSION [--out …]
    python3 build/scripts/plugin-usage-map.py --wiring-only [--out …]
"""
import argparse
import importlib.util
import json
import re
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

from plugin_layout import PLUGIN_DIR

ROOT = Path(__file__).resolve().parents[2]
PLUGIN = ROOT / PLUGIN_DIR
DEFAULT_OUT = ROOT / "jeffrey-pages/src/data/plugin-usage.json"
SUITES = {"evals": "mocked", "evals-live": "live"}
RESULTS_GLOB = "results/*/aggregate-result.json"

JEFFREY_PREFIX = "mcp__plugin_microscope_jeffrey__"
SKILL_TOOL = "Skill"
AGENT_TOOL = "Agent"
IGNORED_TOOLS = {"ToolSearch"}
DEFAULT_AGENT = "general-purpose"
MAIN_LANE = "main"
INPUT_CHARS = 160
KIND_PREFIX = {"skill": "s:", "agent": "a:", "jeffrey": "t:"}
AGENT_ALIASES = {"profile-analyst": "jfr-analyst", "heap-triage": "heap-analyst"}

# The plugin's two areas, each worked by its own agent, and what both of them use. Every skill and every
# tool family belongs to exactly one; test-plugin-usage-map.py fails on one that is new or gone.
AREAS = [
    {"name": "jfr", "label": "JFR recordings", "agent": "jfr-analyst",
     "skills": ["analyze-exceptions", "analyze-jfr", "code-insight", "compare-jfr", "jfr-sql", "optimize-code",
                "profile-run", "regression-check"],
     "families": ["blocking", "code", "compare", "flamegraph", "grpc", "http", "ide", "io", "jdbc", "jfr", "jvm",
                  "memory", "methodtracing", "timeline", "traces"]},
    {"name": "heap", "label": "Heap dumps", "agent": "heap-analyst",
     "skills": ["analyze-heap", "heap-sql"],
     "families": ["heap"]},
    {"name": "shared", "label": "Shared", "agent": None,
     "skills": ["analyze-connector", "help", "report"],
     "families": ["connector", "operations", "profiles", "recordings"]},
]

FRONTMATTER = re.compile(r"^---\n(.*?)\n---\n", re.S)
BACKTICKED_NAME = re.compile(r"`(?:microscope:)?([a-z][a-z-]+)`")

spec = importlib.util.spec_from_file_location("references", ROOT / "build/scripts/check-plugin-references.py")
references = importlib.util.module_from_spec(spec)
spec.loader.exec_module(references)


# ---------- the wiring as the plugin describes it ----------

def frontmatter(text: str) -> str:
    match = FRONTMATTER.match(text)
    return match.group(1) if match else ""


def field(front: str, key: str) -> str | None:
    match = re.search(rf"^{key}:\s*(.*)$", front, re.M)
    return match.group(1).strip().strip('"') if match else None


def tool_mentions(text: str, tool_names: set[str]) -> dict[str, int]:
    """How often each snapshot tool is named outside code fences, client prefixes stripped."""
    body = references.without_prefixes(references.without_fences(text))
    return dict(Counter(name for name in references.TOOL_REFERENCE.findall(body) if name in tool_names))


def name_mentions(text: str, own: str, names: list[str]) -> dict[str, int]:
    """How often each other skill or agent is named in backticks, `microscope:` optional."""
    found = Counter(n for n in BACKTICKED_NAME.findall(references.without_fences(text)) if n != own)
    return {n: c for n, c in found.items() if n in names}


def agent_tools(front: str, key: str, tool_names: set[str]) -> list[str]:
    """An agent's `tools:` or `disallowedTools:` list, its wildcards expanded against the snapshot."""
    listed = re.search(rf"^{key}:\n((?:  - .+\n)+)", front + "\n", re.M)
    if listed:
        items = [line.strip()[2:].strip() for line in listed.group(1).splitlines()]
    else:
        inline = field(front, key)
        items = [item.strip() for item in inline.split(",")] if inline else []
    expanded = set()
    for item in items:
        for prefix in references.CLIENT_PREFIXES:
            if item.startswith(prefix):
                pattern = re.escape(item[len(prefix):]).replace(r"\*", ".*")
                expanded |= {name for name in tool_names if re.fullmatch(pattern, name)}
    return sorted(expanded)


def tools_of(snapshot: dict) -> list[dict]:
    tools = []
    for tool in snapshot["tools"]:
        hints, meta = tool.get("annotations", {}), tool.get("_meta", {})
        tools.append({"name": tool["name"], "title": tool.get("title"), "family": tool["name"].split("_", 1)[0],
                      "readOnly": hints.get("readOnlyHint", False), "destructive": hints.get("destructiveHint", False),
                      "cost": meta.get("jeffrey/cost"), "requires": meta.get("jeffrey/requires") or []})
    return tools


def skills_of(plugin: Path, tool_names: set[str], skill_names: list[str], agent_names: list[str]) -> list[dict]:
    skills = []
    for name in skill_names:
        directory = plugin / "skills" / name
        text = (directory / "SKILL.md").read_text()
        extra = "".join(p.read_text() for p in sorted((directory / "references").glob("*.md")))
        everything = text + extra
        skills.append({"name": name, "description": field(frontmatter(text), "description"),
                       "lines": text.count("\n"), "tools": tool_mentions(everything, tool_names),
                       "skills": name_mentions(everything, name, skill_names),
                       "agents": name_mentions(everything, name, agent_names)})
    return skills


def agents_of(plugin: Path, tool_names: set[str], skill_names: list[str], agent_names: list[str]) -> list[dict]:
    agents = []
    for name in agent_names:
        text = (plugin / "agents" / f"{name}.md").read_text()
        front = frontmatter(text)
        denied = agent_tools(front, "disallowedTools", tool_names)
        agents.append({"name": name, "description": field(front, "description"),
                       "tools": [t for t in agent_tools(front, "tools", tool_names) if t not in denied],
                       "denied": denied, "named": tool_mentions(text, tool_names),
                       "skills": name_mentions(text, name, skill_names),
                       "agents": name_mentions(text, name, agent_names)})
    return agents


# ---------- the wiring as the runs used it ----------

def short(value) -> str:
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    return text if len(text) <= INPUT_CHARS else text[:INPUT_CHARS - 1] + "…"


def without_namespace(name: str) -> str:
    return name.split(":", 1)[1] if ":" in name else name


def epoch_ms(timestamp: str) -> float:
    return datetime.fromisoformat(timestamp.replace("Z", "+00:00")).timestamp() * 1000


def steps_of(lines) -> tuple[list[dict], dict[str, str]]:
    """The ordered tool calls of one stream-json trace, and its lanes (the main one and one per agent call).

    Each Skill, Agent and Jeffrey call carries `by`: the skill active in its lane before it, or the lane's
    agent. A lane's skill is the last Skill called in it. Agent names stay as recorded; aliases belong
    to the current wiring, not the historical steps or lanes.
    """
    steps, start, agent_lanes = [], None, {}
    for line in lines:
        try:
            event = json.loads(line)
        except ValueError:
            continue
        if event.get("type") != "assistant":
            continue
        at = epoch_ms(event["timestamp"]) if event.get("timestamp") else None
        lane = event.get("parent_tool_use_id") or MAIN_LANE
        for block in event.get("message", {}).get("content", []):
            if block.get("type") != "tool_use" or block.get("name") in IGNORED_TOOLS:
                continue
            name, arguments = block.get("name", ""), block.get("input") or {}
            if start is None and at is not None:
                start = at
            step = {"t": round(at - start) if at is not None else None, "lane": lane}
            if name == SKILL_TOOL:
                step |= {"kind": "skill", "name": without_namespace(arguments.get("skill", "?")),
                         "input": short(arguments.get("args", ""))}
            elif name == AGENT_TOOL:
                agent = without_namespace(arguments.get("subagent_type", DEFAULT_AGENT))
                agent_lanes[block.get("id")] = agent
                step |= {"kind": "agent", "name": agent, "input": short(arguments.get("description", ""))}
            elif name.startswith(JEFFREY_PREFIX):
                step |= {"kind": "jeffrey", "name": name[len(JEFFREY_PREFIX):], "input": short(arguments)}
            else:
                step |= {"kind": "other", "name": name, "input": short(arguments)}
            steps.append(step)
    active = {}
    for step in steps:
        owner = ["agent", agent_lanes[step["lane"]]] if step["lane"] in agent_lanes else None
        if step["kind"] != "other":
            step["by"] = active.get(step["lane"]) or owner
        if step["kind"] == "skill":
            active[step["lane"]] = ["skill", step["name"]]
    return steps, {MAIN_LANE: MAIN_LANE} | agent_lanes


def node_of(by: list[str]) -> str:
    return KIND_PREFIX[by[0]] + by[1]


def usage_of(plugin: Path) -> dict:
    """Totals over every run whose trace is still on disk, and each case's latest runs as replays."""
    totals = {kind: Counter() for kind in ("skill", "agent", "jeffrey", "other")}
    observed = defaultdict(Counter)
    latest, runs, missing = {}, 0, 0
    for suite, label in SUITES.items():
        for result in sorted((plugin / suite).glob(RESULTS_GLOB)):
            report = json.loads(result.read_text())
            for case in report["cases"]:
                replays = []
                for number, run in enumerate(case["arms"]["with"], start=1):
                    trace = Path(run.get("tracePath") or "")
                    if not trace.is_file():
                        missing += 1
                        continue
                    with trace.open() as lines:
                        steps, lanes = steps_of(lines)
                    runs += 1
                    for step in steps:
                        totals[step["kind"]][step["name"]] += 1
                        by = step.get("by")
                        if by and not (step["kind"] == "skill" and by == ["skill", step["name"]]):
                            observed[node_of(by)][KIND_PREFIX[step["kind"]] + step["name"]] += 1
                    replays.append({"run": number, "score": run.get("score"), "passed": run.get("passed"),
                                    "turns": run.get("turns"), "costUsd": run.get("costUsd"),
                                    "durationSeconds": run.get("durationSeconds"),
                                    "graders": [{"name": g["name"], "passed": g.get("passed")}
                                                for g in run.get("graders", [])],
                                    "lanes": lanes, "steps": steps})
                if replays:
                    latest[(label, case["name"])] = {"suite": label, "case": case["name"],
                                                     "prompt": case.get("promptMarkdown", ""),
                                                     "at": report.get("startedAt"), "runs": replays}
    return {"usage": {"runs": runs, "missingTraces": missing} | {k: dict(v) for k, v in totals.items()},
            "observed": {k: dict(v) for k, v in observed.items()},
            "replays": [latest[key] for key in sorted(latest)]}


def wiring_of(plugin: Path, snapshot: dict) -> dict:
    """The current documented wiring, its plugin version, and links from historical agent names."""
    tools = tools_of(snapshot)
    tool_names = {tool["name"] for tool in tools}
    skill_names = sorted(p.parent.name for p in (plugin / "skills").glob("*/SKILL.md"))
    agent_names = sorted(p.stem for p in (plugin / "agents").glob("*.md"))
    return {"wiringPlugin": json.loads((plugin / "plugin.json").read_text()).get("version"),
            "agentAliases": AGENT_ALIASES,
            "tools": tools, "skills": skills_of(plugin, tool_names, skill_names, agent_names),
            "agents": agents_of(plugin, tool_names, skill_names, agent_names), "areas": AREAS}


def usage_map(plugin: Path, snapshot: dict, recorded_version: str) -> dict:
    return ({"plugin": recorded_version} | wiring_of(plugin, snapshot) | usage_of(plugin))


def to_json(data: dict) -> str:
    return json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="the JSON to write")
    parser.add_argument("--wiring-only", action="store_true",
                        help="rewrite only the documented wiring of --out, keeping its runs; needs no traces")
    parser.add_argument("--recorded-version", help="the plugin version used for every ingested trace; required for full generation")
    args = parser.parse_args(argv)
    if args.wiring_only and args.recorded_version is not None:
        parser.error("--recorded-version cannot be combined with --wiring-only; the recorded version stays unchanged")
    if not args.wiring_only and not args.recorded_version:
        parser.error("--recorded-version is required for full generation; supply the plugin version used for every ingested trace")
    snapshot = json.loads((ROOT / references.TOOLS_SNAPSHOT).read_text())
    if args.wiring_only:
        if not args.out.is_file():
            print(f"No map to refresh at {args.out}: run with --recorded-version VERSION and without --wiring-only first.",
                  file=sys.stderr)
            return 1
        # "plugin" stays: it is the version the kept runs were recorded with.
        data = json.loads(args.out.read_text()) | wiring_of(PLUGIN, snapshot)
    else:
        data = usage_map(PLUGIN, snapshot, args.recorded_version)
        if not data["usage"]["runs"]:
            print("No eval trace is on disk: run `claude plugin eval … --keep-temp` first "
                  f"({data['usage']['missingTraces']} runs had their traces removed).", file=sys.stderr)
            return 1
    usage = data["usage"]
    args.out.write_text(to_json(data))
    print(f"{len(data['skills'])} skills, {len(data['agents'])} agents, {len(data['tools'])} tools in "
          f"{len({t['family'] for t in data['tools']})} families; {usage['runs']} runs "
          f"({usage['missingTraces']} without a trace), {sum(usage['skill'].values())} skill invocations, "
          f"{sum(usage['agent'].values())} agent hand-offs, {len(data['replays'])} replayed cases -> {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
