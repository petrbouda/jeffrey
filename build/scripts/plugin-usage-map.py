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

The JSON keeps every run's totals and, for each eval case, the latest results' runs step by step, each with
its final reply and every grader's reason, so a failed run can still be read once its trace is gone. It
is written into jeffrey-pages, whose Agent Plugin page (`/agent-plugin`) replays the runs over a map
of the wiring. Traces live in temporary directories, so a run whose trace is gone is skipped.
Full generation requires `--recorded-version`: every ingested trace must come from that plugin
version. Trace metadata does not identify it, so the current manifest cannot supply it.

`--wiring-only` needs no traces: it refreshes the documented wiring and its `wiringPlugin` version
at `--out`, keeping the recorded `plugin` version and runs. `agentAliases` links historical agent
names to current wiring without changing the recorded names. `test-plugin-usage-map.py` fails until
the wiring is refreshed after a skill, agent or tool change.

Each replay keeps a digest of what its runs depended on (`INPUT_GROUPS`); `--stale` lists the cases whose inputs
have moved since, which `refresh-plugin-evals.py` runs again.

Usage:
    python3 build/scripts/plugin-usage-map.py --recorded-version VERSION [--out …]
    python3 build/scripts/plugin-usage-map.py --wiring-only [--out …]
    python3 build/scripts/plugin-usage-map.py --stale [--github] [--strict]
"""
import argparse
import hashlib
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
# What a run kept to be read after the trace is gone: the reply the graders read, and each grader's reason (the
# judge's explanation, or what a deterministic grader matched). Cut so the page stays light; the full lengths
# are kept beside them.
REPLY_CHARS = 6000
EXPLANATION_CHARS = 1500
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
# The agents the plugin ships, under their current and their historical names. A run may also hand work to
# one of the client's own (Claude Code's Explore, general-purpose): that is a call like any other tool's,
# not a use of the plugin's agents.
PLUGIN_AGENTS = {area["agent"] for area in AREAS if area.get("agent")} | set(AGENT_ALIASES)

# What each eval case runs on, declared as one tag in its prompt's frontmatter: a real service's recorded
# runs, or the made-up workload written for the evals. The page marks every case with it; a case that reads
# no recording at all is named in NO_RECORDING instead, and test-plugin-usage-map.py fails on a case that is
# in neither.
WORKLOADS = [
    {"name": "checkout", "label": "checkout", "real": False,
     "detail": "A made-up checkout service: two Java files written for the evals, with a planted CPU hotspot, "
               "lock contention, exceptions, a slow leak, an N+1 and a regression between two runs."},
    {"name": "persons", "label": "persons service", "real": True,
     "detail": "Runs of jeffrey-testapp, a persons REST service with a database, under load and traced: "
               "direct and dom profiled by the provisioner, and the n-plus-one, lock and exceptions modes "
               "(one problem planted each) recorded with JFR under the testapp's own client."},
]
NO_RECORDING = {"record-exceptions-next-time"}

# The question a reader brings, which the page groups the cases by: every eval case of both suites sits in exactly
# one use case (test-plugin-usage-map.py fails on one that is in none or in two); `example` is the case whose
# question the overview quotes for it. Held here rather than as a tag in
# each prompt, because a prompt is part of its case's digest: retagging would mark every replay stale.
USE_CASES = [
    {"name": "cpu", "example": "endpoint-cpu", "label": "Where the CPU goes", "icon": "bi-cpu",
     "question": "Which code burns the CPU, and is it ours?",
     "cases": ["endpoint-cpu", "hot-methods", "fix-it", "method-worth-it", "two-areas-delegated",
               "cpu-spike-window"]},
    {"name": "slow-requests", "example": "slow-endpoints", "label": "Slow requests and waiting", "icon": "bi-hourglass-split",
     "question": "Which requests are slow, and where do their threads wait?",
     "cases": ["slow-endpoints", "what-runs", "wall-vs-cpu", "lock-contention", "network-or-disk"]},
    {"name": "database", "example": "n-plus-one", "label": "Database", "icon": "bi-database",
     "question": "What does the service ask the database, how often, and how long does it take?",
     "cases": ["count-queries", "n-plus-one", "pool-latency", "slow-sql-statements"]},
    {"name": "memory", "example": "heap-dump-what-holds-memory", "label": "Memory", "icon": "bi-memory",
     "question": "What allocates, and what holds the heap?",
     "cases": ["allocation-hotspots", "heap-dump-what-holds-memory", "heap-offers-implement-fixes",
               "orders-by-coupon-in-sql"]},
    {"name": "jvm-health", "example": "gc-pauses", "label": "JVM health", "icon": "bi-heart-pulse",
     "question": "Is the JVM itself healthy: GC, the container, its threads?",
     "cases": ["gc-pauses", "jvm-health", "threads-stuck"]},
    {"name": "exception-cost", "example": "exception-sites", "label": "Exceptions", "icon": "bi-exclamation-triangle",
     "question": "Where are exceptions thrown, and do they cost anything?",
     "cases": ["exception-sites", "exception-inputs-counted-in-sql", "record-exceptions-next-time"]},
    {"name": "regression", "example": "dom-regression", "label": "Did it regress?", "icon": "bi-arrow-left-right",
     "question": "Did my change make it slower, and where?",
     "cases": ["did-it-regress", "dom-regression", "two-revisions", "did-my-last-commit-make-it-worse",
               "compare-the-methods-my-diff-touched", "regression-call-paths"]},
    {"name": "getting-started", "example": "help-what-is-here", "label": "Getting started", "icon": "bi-compass",
     "question": "What is here, how do I record it, and what should I change?",
     "cases": ["help-what-is-here", "open-question", "why-slow", "what-to-change", "nothing-profiled",
               "no-connector", "write-report", "per-call-timing-not-recorded"]},
]
# Each case's question in a line, as the page lists it under its use case; the prompt itself is the full text.
CASE_QUESTIONS = {
    "endpoint-cpu": "Where does the CPU time go?",
    "hot-methods": "Which of our own methods does the CPU go to?",
    "fix-it": "Where does the CPU go? This is the repository",
    "method-worth-it": "Is PersonController.toJsonTree worth optimizing?",
    "two-areas-delegated": "CPU and allocation, both",
    "cpu-spike-window": "Was there a moment the CPU spiked, and what ran then?",
    "slow-endpoints": "Which endpoints are much slower than the rest, and why?",
    "what-runs": "What does GET /persons/{count} actually run?",
    "wall-vs-cpu": "Requests feel slow but the CPU is low: where do the threads wait?",
    "lock-contention": "Which locks hold the threads back?",
    "network-or-disk": "Do the requests wait on the network or on the disk?",
    "count-queries": "Why far more COUNT queries than count requests?",
    "n-plus-one": "Does GET /persons/{count} have an N+1?",
    "pool-latency": "Is the connection pool what makes requests slow?",
    "slow-sql-statements": "Which SQL statements cost the most time?",
    "allocation-hotspots": "What allocates the most? This is the repository",
    "heap-dump-what-holds-memory": "What holds the memory in this heap dump?",
    "heap-offers-implement-fixes": "What holds the memory? This is the repository",
    "orders-by-coupon-in-sql": "How many orders carry a SPRING- coupon? (SQL over the heap)",
    "gc-pauses": "Are GC pauses hurting it?",
    "jvm-health": "Before we scale up: is the JVM healthy?",
    "threads-stuck": "Are any threads stuck or deadlocked?",
    "exception-sites": "Why so many exceptions?",
    "exception-inputs-counted-in-sql": "NumberFormatExceptions: a few inputs or many? (SQL)",
    "record-exceptions-next-time": "How do I record the exception types next time?",
    "did-it-regress": "Did my last change make it slower?",
    "dom-regression": "The new build is slower: where?",
    "two-revisions": "Did my last commit make it slower? Nothing is profiled yet",
    "did-my-last-commit-make-it-worse": "Did my pricing change make checkout costlier?",
    "compare-the-methods-my-diff-touched": "Did reprice and formatLine get more expensive?",
    "regression-call-paths": "Through which call path did the extra time come?",
    "help-what-is-here": "What can Jeffrey do, and what is in it?",
    "open-question": "What is wrong with this recording?",
    "why-slow": "It feels slow: can Jeffrey say why?",
    "what-to-change": "What should I change? The code is not checked out",
    "nothing-profiled": "How do I record it so Jeffrey can analyse it?",
    "no-connector": "Why was it slow in production this morning?",
    "write-report": "Write up what I found, for my team",
    "per-call-timing-not-recorded": "How long does PersonRepository.count take per call?",
}

# What a replay's runs depended on, by group, so a later change to any of them marks the replay stale (`--stale`):
# the skills the runs reached and the plugin agents they handed work to, every skill's description (which skill a
# prompt reaches at all), the case's own files (prompt, scaffold, graders), and the answers the runs were given —
# the recorded mocks for the mocked suite, the tools snapshot of the live Jeffrey for the live one.
INPUT_GROUPS = ("skills", "agents", "routing", "case", "answers")
SUITE_DIRS = {label: suite for suite, label in SUITES.items()}
MOCKS_DIR = "mocks"
FINGERPRINT_CHARS = 16

FRONTMATTER = re.compile(r"^---\n(.*?)\n---\n", re.S)
TAG_LIST = re.compile(r"^\[(.*)\]$")
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
    return cut(text, INPUT_CHARS)


def cut(text: str, limit: int) -> str:
    return text if len(text) <= limit else text[:limit - 1] + "…"


def reply_of(lines) -> str | None:
    """The run's last reply in the main conversation — what a `last_message` grader reads: the text of the last
    assistant message there that holds any. None when the run never wrote one."""
    reply = None
    for line in lines:
        try:
            event = json.loads(line)
        except ValueError:
            continue
        if event.get("type") != "assistant" or event.get("parent_tool_use_id"):
            continue
        texts = [block.get("text", "") for block in event.get("message", {}).get("content", [])
                 if block.get("type") == "text" and block.get("text", "").strip()]
        if texts:
            reply = "\n\n".join(texts)
    return reply


def grader_of(grader: dict) -> dict:
    """A grader's verdict and, when it gave one, its reason, cut to EXPLANATION_CHARS."""
    explanation = grader.get("explanation")
    if not isinstance(explanation, str) or not explanation.strip():
        return {"name": grader["name"], "passed": grader.get("passed")}
    return {"name": grader["name"], "passed": grader.get("passed"),
            "explanation": cut(explanation.strip(), EXPLANATION_CHARS)}


def without_namespace(name: str) -> str:
    return name.split(":", 1)[1] if ":" in name else name


def epoch_ms(timestamp: str) -> float:
    return datetime.fromisoformat(timestamp.replace("Z", "+00:00")).timestamp() * 1000


def steps_of(lines) -> tuple[list[dict], dict[str, str]]:
    """The ordered tool calls of one stream-json trace, and its lanes (the main one and one per agent call).

    Each Skill, Agent and Jeffrey call carries `by`: the skill active in its lane before it, or the lane's
    agent. A lane's skill is the last Skill called in it. Agent names stay as recorded; aliases belong
    to the current wiring, not the historical steps or lanes. A hand-off to an agent the plugin does not ship
    is an `other` step, and its lane carries no agent.
    """
    steps, start, agent_lanes, client_lanes = [], None, {}, {}
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
            elif name == AGENT_TOOL and without_namespace(arguments.get("subagent_type", DEFAULT_AGENT)) in PLUGIN_AGENTS:
                agent = without_namespace(arguments["subagent_type"])
                agent_lanes[block.get("id")] = agent
                step |= {"kind": "agent", "name": agent, "input": short(arguments.get("description", ""))}
            elif name == AGENT_TOOL:
                agent = without_namespace(arguments.get("subagent_type", DEFAULT_AGENT))
                client_lanes[block.get("id")] = agent
                step |= {"kind": "other", "name": f"{AGENT_TOOL}({agent})", "input": short(arguments.get("description", ""))}
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
    return steps, {MAIN_LANE: MAIN_LANE} | client_lanes | agent_lanes


def digest(parts: list[tuple[str, bytes]]) -> str:
    """One short sha256 over named byte strings, in name order: the same inputs always give the same digest."""
    hashed = hashlib.sha256()
    for name, data in sorted(parts):
        hashed.update(name.encode() + b"\0" + hashlib.sha256(data).digest())
    return hashed.hexdigest()[:FINGERPRINT_CHARS]


def tree(root: Path, base: Path) -> list[tuple[str, bytes]]:
    """Every file under `root`, named relative to `base`, leaving out dot entries (a harness's caches); a missing root
    is one named marker, so a skill or agent that is gone changes the digest too."""
    if not root.exists():
        return [(f"missing:{root.relative_to(base)}", b"")]
    if root.is_file():
        return [(str(root.relative_to(base)), root.read_bytes())]
    return [(str(path.relative_to(base)), path.read_bytes()) for path in sorted(root.rglob("*"))
            if path.is_file() and not any(part.startswith(".") for part in path.relative_to(root).parts)]


def reached(runs: list[dict]) -> tuple[set[str], set[str]]:
    """The skills and the plugin agents a replay's runs reached, agents under their current names."""
    skills = {step["name"] for run in runs for step in run["steps"] if step["kind"] == "skill"}
    agents = {AGENT_ALIASES.get(step["name"], step["name"]) for run in runs for step in run["steps"]
              if step["kind"] == "agent"}
    return skills, agents


def case_inputs(plugin: Path, suite: str, case: str, runs: list[dict]) -> dict[str, str]:
    """The digest of each group of inputs a case's runs depended on (`INPUT_GROUPS`)."""
    skills, agents = reached(runs)
    suite_dir = plugin / SUITE_DIRS[suite]
    descriptions = [(path.parent.name, (field(frontmatter(path.read_text()), "description") or "").encode())
                    for path in sorted((plugin / "skills").glob("*/SKILL.md"))]
    answers = tree(suite_dir / MOCKS_DIR, plugin) if suite == "mocked" else tree(ROOT / references.TOOLS_SNAPSHOT, ROOT)
    return {"skills": digest([part for skill in skills for part in tree(plugin / "skills" / skill, plugin)]),
            "agents": digest([part for agent in agents for part in tree(plugin / "agents" / f"{agent}.md", plugin)]),
            "routing": digest(descriptions),
            "case": digest(tree(suite_dir / "cases" / case, plugin)),
            "answers": digest(answers)}


def stale_replays(plugin: Path, data: dict) -> list[dict]:
    """Every current case whose replay was recorded on other inputs than the plugin holds now, with the groups that
    changed — or `None` for a replay recorded before inputs were kept — and the cases with no replay at all."""
    current = {(label, prompt.parent.name) for suite, label in SUITES.items()
               for prompt in (plugin / suite / "cases").glob("*/prompt.md")}
    replays = {(replay["suite"], replay["case"]): replay for replay in data.get("replays", [])}
    stale = []
    for suite, case in sorted(current):
        replay = replays.get((suite, case))
        if replay is None:
            stale.append({"suite": suite, "case": case, "changed": ["no replay"]})
            continue
        recorded = replay.get("inputs")
        now = case_inputs(plugin, suite, case, replay["runs"])
        changed = None if recorded is None else [group for group in INPUT_GROUPS if recorded.get(group) != now[group]]
        if changed is None or changed:
            stale.append({"suite": suite, "case": case, "changed": changed})
    return stale


def node_of(by: list[str]) -> str:
    return KIND_PREFIX[by[0]] + by[1]


def case_tags(prompt: Path) -> list[str]:
    tags = field(frontmatter(prompt.read_text()), "tags") or ""
    match = TAG_LIST.match(tags)
    return [tag.strip() for tag in match.group(1).split(",")] if match else []


def case_workloads(plugin: Path) -> dict[str, str | None]:
    """Each eval case of both suites, by name, with the workload its prompt's tags declare (None: no recording)."""
    names = {workload["name"] for workload in WORKLOADS}
    cases = {}
    for suite in SUITES:
        for prompt in sorted((plugin / suite / "cases").glob("*/prompt.md")):
            declared = [tag for tag in case_tags(prompt) if tag in names]
            if len(declared) > 1:
                raise ValueError(f"{prompt}: more than one workload tag {declared}")
            cases[prompt.parent.name] = declared[0] if declared else None
    return cases


def case_names(plugin: Path) -> list[str]:
    """Every eval case of both suites, by name."""
    return sorted(prompt.parent.name for suite in SUITES for prompt in (plugin / suite / "cases").glob("*/prompt.md"))


def use_cases_of(plugin: Path) -> dict:
    """The use cases as the page shows them, each case's use case and its question in a line — only for cases the
    suites hold, so a case named here before its directory exists does not reach the page."""
    present = set(case_names(plugin))
    return {"useCases": [{key: value for key, value in use_case.items() if key != "cases"} for use_case in USE_CASES],
            "caseUseCases": {case: use_case["name"] for use_case in USE_CASES for case in use_case["cases"]
                             if case in present},
            "caseQuestions": {case: question for case, question in CASE_QUESTIONS.items() if case in present}}


def usage_of(plugin: Path, recorded_version: str | None = None, earlier: dict | None = None) -> dict:
    """Each current case's latest runs whose traces are still on disk, as replays, and the totals over exactly
    those runs: a case run again replaces its earlier runs rather than adding to them, a case no longer in its
    suite is left out (one moved to the other suite counts there), and so is a run the harness refused.
    A current case with no runs on disk keeps its replay from the `earlier` map, with the plugin version it was
    recorded with, so a suite that cannot run here does not drop off the page."""
    current = {(label, prompt.parent.name) for suite, label in SUITES.items()
               for prompt in (plugin / suite / "cases").glob("*/prompt.md")}
    latest = {}
    for suite, label in SUITES.items():
        for result in sorted((plugin / suite).glob(RESULTS_GLOB)):
            report = json.loads(result.read_text())
            for case in report["cases"]:
                if (label, case["name"]) not in current:
                    continue
                replays, missing = [], 0
                for number, run in enumerate(case["arms"]["with"], start=1):
                    if run.get("error"):
                        # The harness refused or aborted it: nothing the plugin did is in it.
                        continue
                    trace = Path(run.get("tracePath") or "")
                    if not trace.is_file():
                        missing += 1
                        continue
                    lines = trace.read_text().splitlines()
                    steps, lanes = steps_of(lines)
                    reply = reply_of(lines)
                    replays.append({"run": number, "score": run.get("score"), "passed": run.get("passed"),
                                    "turns": run.get("turns"), "costUsd": run.get("costUsd"),
                                    "durationSeconds": run.get("durationSeconds"),
                                    "graders": [grader_of(g) for g in run.get("graders", [])],
                                    "reply": cut(reply, REPLY_CHARS) if reply is not None else None,
                                    "replyChars": len(reply) if reply is not None else 0,
                                    "lanes": lanes, "steps": steps})
                if replays:
                    latest[(label, case["name"])] = ({"suite": label, "case": case["name"],
                                                      "prompt": case.get("promptMarkdown", ""),
                                                      "at": report.get("startedAt"), "plugin": recorded_version,
                                                      "inputs": case_inputs(plugin, label, case["name"], replays),
                                                      "runs": replays}, missing)
    for replay in (earlier or {}).get("replays", []):
        key = (replay["suite"], replay["case"])
        if key in current and key not in latest:
            latest[key] = (replay | {"plugin": replay.get("plugin") or earlier.get("plugin")}, 0)
    totals = {kind: Counter() for kind in ("skill", "agent", "jeffrey", "other")}
    observed = defaultdict(Counter)
    for replay, _ in latest.values():
        for run in replay["runs"]:
            for step in run["steps"]:
                totals[step["kind"]][step["name"]] += 1
                by = step.get("by")
                if by and not (step["kind"] == "skill" and by == ["skill", step["name"]]):
                    observed[node_of(by)][KIND_PREFIX[step["kind"]] + step["name"]] += 1
    runs = sum(len(replay["runs"]) for replay, _ in latest.values())
    missing = sum(count for _, count in latest.values())
    versions = sorted({replay["plugin"] for replay, _ in latest.values() if replay.get("plugin")})
    return {"pluginVersions": versions,
            "usage": {"runs": runs, "missingTraces": missing} | {k: dict(v) for k, v in totals.items()},
            "observed": {k: dict(v) for k, v in observed.items()},
            "replays": [latest[key][0] for key in sorted(latest)]}


def wiring_of(plugin: Path, snapshot: dict) -> dict:
    """The current documented wiring, its plugin version, and links from historical agent names."""
    tools = tools_of(snapshot)
    tool_names = {tool["name"] for tool in tools}
    skill_names = sorted(p.parent.name for p in (plugin / "skills").glob("*/SKILL.md"))
    agent_names = sorted(p.stem for p in (plugin / "agents").glob("*.md"))
    return {"wiringPlugin": json.loads((plugin / "plugin.json").read_text()).get("version"),
            "agentAliases": AGENT_ALIASES,
            "tools": tools, "skills": skills_of(plugin, tool_names, skill_names, agent_names),
            "agents": agents_of(plugin, tool_names, skill_names, agent_names), "areas": AREAS,
            "workloads": WORKLOADS, "caseWorkloads": case_workloads(plugin)} | use_cases_of(plugin)


def usage_map(plugin: Path, snapshot: dict, recorded_version: str, earlier: dict | None = None) -> dict:
    return ({"plugin": recorded_version} | wiring_of(plugin, snapshot) | usage_of(plugin, recorded_version, earlier))


def to_json(data: dict) -> str:
    return json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def report_stale(out: Path, github: bool, strict: bool) -> int:
    """`--stale`: each case whose replay no longer describes the plugin, and the inputs that moved under it."""
    if not out.is_file():
        print(f"No map at {out}.", file=sys.stderr)
        return 1
    stale = stale_replays(PLUGIN, json.loads(out.read_text()))
    for entry in stale:
        why = "recorded before inputs were kept" if entry["changed"] is None else "changed: " + ", ".join(entry["changed"])
        line = f"{entry['suite']} case {entry['case']}: its Agent Plugin page replay is stale ({why})"
        print(f"::warning file={out.relative_to(ROOT)}::{line}" if github else line)
    if not stale:
        print("Every case's replay was recorded on the inputs the plugin holds now.")
    else:
        print(f"{len(stale)} case(s) to run again: python3 build/scripts/refresh-plugin-evals.py", file=sys.stderr)
    return 1 if stale and strict else 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="the JSON to write")
    parser.add_argument("--wiring-only", action="store_true",
                        help="rewrite only the documented wiring of --out, keeping its runs; needs no traces")
    parser.add_argument("--recorded-version", help="the plugin version used for every ingested trace; required for full generation")
    parser.add_argument("--drop-earlier", action="store_true",
                        help="keep no replay from the existing map: a case with no runs on disk leaves the page")
    parser.add_argument("--stale", action="store_true",
                        help="write nothing: list the cases whose replays were recorded on other inputs than the plugin "
                             "holds now, and what changed")
    parser.add_argument("--github", action="store_true", help="with --stale: one GitHub Actions warning per case")
    parser.add_argument("--strict", action="store_true", help="with --stale: exit 1 when a case is stale")
    args = parser.parse_args(argv)
    if args.stale:
        return report_stale(args.out, args.github, args.strict)
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
        earlier = json.loads(args.out.read_text()) if args.out.is_file() and not args.drop_earlier else None
        data = usage_map(PLUGIN, snapshot, args.recorded_version, earlier)
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
