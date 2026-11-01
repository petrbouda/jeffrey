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
"""Records the MCP answers the microscope plugin's evals replay, from a real Jeffrey.

`claude plugin eval` never starts the plugin's server: every tool a case may call is answered by a
mock under `jeffrey-claude-plugin/evals/mocks/jeffrey/`, and a tool with no mock is not offered at
all. Hand-written answers would test the skills against a server that does not exist, so this script
takes them from one that does:

1. records the eval workload (`evals/fixtures/workload`) under JFR twice — `baseline`, then
   `candidate` with its deliberate regression and a heap dump at the end — with jeffrey-events and
   jeffrey-tracing on its class path (`TRACING_JARS`, built from `utilities/`), so the recordings carry
   sampled traces;
2. imports the three files into the Jeffrey at `--url` with `recordings_analyzeFile`, as a user would,
   and prepares the heap dump with `heap_prepare`;
3. calls every tool the way a model following the skills would reach it: each tool whose required
   arguments are only a profile (or a profile pair, or one of an enum's values), for every profile,
   then every call an answer offers in `followUp.nextTools`, `investigationAreas[].nextTools` or
   `topFindings[].nextTool`, breadth first;
4. rewrites the profile ids the cases' graders name to the new ones, and writes one mock per tool
   that answered, addressed by its required arguments and graded against its full input — at most
   `MAX_FIXTURES_PER_TOOL` answers a tool, the first reached —
   (`{{file:fixtures/<tool>-{input.a}-….json}}`), and `_tools.json`, the real `tools/list` for those
   tools, so each mocked tool carries its real description and input schema.

A tool whose required argument takes free text that cannot be part of a file name (a SQL query, a
URI) gets no mock and is absent from the eval runs; so are the writers this script does not call.
The answers describe this Jeffrey's build — re-record after a change to what the tools return.

The SQL cases (`evals-live/`) run against a real Jeffrey instead, which `--load` prepares: it imports
the files a recording left in `--work-dir` (or the copies kept in jeffrey-recordings/plugin-evals,
with the dump gzipped) into an empty Jeffrey, under the names the mocks carry, and
checks the figures those cases' graders name against it. A recording measures those figures
(`FIGURES`) and rewrites them in the graders, as it does the profile ids.

Usage: record-plugin-eval-mocks.py --url http://localhost:8585/api/mcp --work-dir /tmp/jeffrey-eval
                                   [--java /path/to/java] [--seconds 30] [--load]
"""

import argparse
import collections
import gzip
import hashlib
import json
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from decimal import Decimal
from pathlib import Path

PLUGIN_DIR = Path("jeffrey-claude-plugin")
WORKLOAD = PLUGIN_DIR / "evals/fixtures/workload/com/acme/checkout/Checkout.java"
# What the workload's traces are written with: the HTTP exchange event, the Tracer, and where the Tracer keeps
# the span in progress. Built by TRACING_BUILD; the workload runs on JDK 25, but the ThreadLocal storage is
# what jeffrey-tracing's own starter brings, and a recording's spans do not depend on which one holds them.
UTILITIES = Path("utilities")
TRACING_JARS = (
    UTILITIES / "jeffrey-events/target/jeffrey-events-1.0-SNAPSHOT.jar",
    UTILITIES / "jeffrey-tracing-parent/jeffrey-tracing-api/target/jeffrey-tracing-api-1.0-SNAPSHOT.jar",
    UTILITIES / "jeffrey-tracing-parent/jeffrey-tracing-thread-local/target/"
                "jeffrey-tracing-thread-local-1.0-SNAPSHOT.jar",
)
TRACING_BUILD = ("mvn -f utilities/pom.xml -pl jeffrey-events,jeffrey-tracing-parent/jeffrey-tracing-thread-local "
                 "-am package -DskipTests")
MOCKS = PLUGIN_DIR / "evals/mocks/jeffrey"
FIXTURES = MOCKS / "fixtures"
RECORDED_PROFILES = PLUGIN_DIR / "evals/fixtures/recorded-profiles.json"
# Where the recording behind the mocks is kept, for --load on another machine: plugin-evals/ holds the two
# JFR files and the dump gzipped, under the names recorded_files() gives them.
RECORDINGS_REPO = "https://github.com/petrbouda/jeffrey-recordings (plugin-evals/)"
GZIP_SUFFIX = ".gz"
MOCKED_CASES = PLUGIN_DIR / "evals/cases"
# Run with --mocks off, so they have no mock_calls to grade and get no call-contract grader.
LIVE_CASES = PLUGIN_DIR / "evals-live/cases"
CASES = (MOCKED_CASES, LIVE_CASES)

PROTOCOL_VERSION = "2026-07-28"
CLIENT_INFO = {"name": "plugin-eval-recorder", "version": "1"}
# Every throw, on any JDK: JDK 25 turns jdk.JavaExceptionThrow on but throttles it (300/s under
# profile), so #enabled=true alone records a sample; JDK 21 warns that throttle does not exist.
JFR_OPTIONS = ("settings=profile,jdk.JavaExceptionThrow#enabled=true,jdk.JavaExceptionThrow#throttle=off,"
               "dumponexit=true")
REQUEST_TIMEOUT_SECONDS = 180
OPERATION_POLL_SECONDS = 2
OPERATION_FINISHED = ("COMPLETED", "FAILED", "CANCELLED")
OPERATION_BUDGET_SECONDS = 900
MAX_CALLS = 1500
# Breadth first, so the answers kept are the ones nearest the seeds: a heap walk offers a path, the
# referrers and the children of every object it names, and a model reads the first few.
MAX_FIXTURES_PER_TOOL = 8

PROFILE_NAMES = {"baseline": "checkout baseline", "candidate": "checkout candidate", "heap": "checkout heap dump"}

PROFILE_ID = "profileId"
BASELINE_PROFILE_ID = "baselineProfileId"
OPERATION_ID = "operationId"

# The writers are recorded only where this script calls them on purpose — the imports and the heap
# preparation — never from a nextTools entry, which could change the state the other answers describe.
NOT_CALLED_FROM_ANSWERS = {
    "recordings_analyzeFile", "recordings_analyzeRecording", "recordings_delete", "heap_prepare",
    "remote_download", "remote_fetchFile", "operations_cancel", "ide_link", "ide_open",
}
FILE_NAME_SAFE = re.compile(r"[A-Za-z0-9._-]+")
# Text the agent writes itself rather than copies from an answer — a SQL or OQL query. A mock pinned to the
# queries this script happened to run would abort every run that asks its own, so such a tool stays unmocked.
COMPOSED_ARGUMENTS = {"query"}

# The workload's own package, the way analyze-exceptions passes it, so the recorded throw sites name
# their own frame — the first answer recorded for a key is the one a case gets.
APPLICATION_PACKAGES = ["com.acme.checkout."]
# The comparisons the compare and regression cases read, in both directions: compare_list offers one
# event type, and a case may ask about any of these.
COMPARED_EVENT_TYPES = (("jdk.ExecutionSample", False), ("jdk.ObjectAllocationSample", True),
                        ("jdk.JavaMonitorEnter", True))


@dataclass(frozen=True)
class Figure:
    """A count the live cases' graders name, as one SQL query answers it from one of the recordings."""
    name: str
    tool: str
    profile: str
    query: str


ORDERS_COUPONS = ("FROM instance i JOIN class c USING (class_id) "
                  "JOIN outbound_ref r ON r.source_id = i.instance_id AND r.field_kind = 0 "
                  "JOIN class_instance_field f ON f.class_id = c.class_id AND f.field_index = r.field_id "
                  "JOIN string_content s ON s.instance_id = r.target_id "
                  "WHERE c.name = 'com.acme.checkout.Checkout$Order' AND f.name = 'coupon'")
FIGURES = (
    Figure("orders", "heap_executeQuery", "heap", f"SELECT COUNT(*) {ORDERS_COUPONS}"),
    Figure("promoOrders", "heap_executeQuery", "heap", f"SELECT COUNT(*) {ORDERS_COUPONS} AND s.content LIKE 'SPRING-%'"),
)


@dataclass(frozen=True)
class Profiles:
    baseline: str
    candidate: str
    heap: str

    @property
    def all(self) -> tuple[str, ...]:
        return self.baseline, self.candidate, self.heap


@dataclass
class Recorded:
    """Each tool's answers by the values of its required arguments, in the order they were made."""
    answers: dict[str, dict[tuple, str]] = field(default_factory=lambda: collections.defaultdict(dict))
    fixed: dict[str, str] = field(default_factory=dict)
    inputs: dict[str, dict[tuple, dict]] = field(default_factory=lambda: collections.defaultdict(dict))


class Jeffrey:
    def __init__(self, url: str):
        self.url = url
        self.next_id = 0

    def request(self, method: str, params: dict) -> dict:
        self.next_id += 1
        meta = {"io.modelcontextprotocol/protocolVersion": PROTOCOL_VERSION,
                "io.modelcontextprotocol/clientCapabilities": {},
                "io.modelcontextprotocol/clientInfo": CLIENT_INFO}
        body = {"jsonrpc": "2.0", "id": self.next_id, "method": method, "params": {**params, "_meta": meta}}
        headers = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream",
                   "MCP-Protocol-Version": PROTOCOL_VERSION, "Mcp-Method": method}
        if method == "tools/call":
            headers["Mcp-Name"] = params["name"]
        request = urllib.request.Request(self.url, data=json.dumps(body).encode(), method="POST", headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                envelope = json.loads(response.read())
        except urllib.error.HTTPError as error:
            envelope = json.loads(error.read() or b"{}")
        if "error" in envelope:
            raise RuntimeError(f"{method} {params.get('name', '')}: {envelope['error']}")
        return envelope["result"]

    def tools(self) -> list[dict]:
        return self.request("tools/list", {})["tools"]

    def call(self, tool: str, arguments: dict) -> dict:
        return self.request("tools/call", {"name": tool, "arguments": arguments})

    def finished(self, answer: dict) -> dict:
        """The answer of a call, or of the operation it started once that has finished."""
        operation = (answer.get("structuredContent") or {}).get(OPERATION_ID)
        deadline = time.monotonic() + OPERATION_BUDGET_SECONDS
        while operation and time.monotonic() < deadline:
            status = self.call("operations_status", {OPERATION_ID: operation})
            # The snapshot names its state `status`; reading any other key returns at once, before the
            # operation has finished.
            if (status.get("structuredContent") or {}).get("status") in OPERATION_FINISHED:
                return status
            time.sleep(OPERATION_POLL_SECONDS)
        if operation:
            raise RuntimeError(f"operation {operation} did not finish within {OPERATION_BUDGET_SECONDS} s")
        return answer


def text_of(answer: dict) -> str:
    return "\n".join(part["text"] for part in answer.get("content", []) if part.get("type") == "text")


def seen_by_model(answer: dict) -> str:
    """What Claude Code hands the model for this answer, which is what a mock must replay.

    A result with structuredContent reaches the model as that JSON, not as its text content: a
    Markdown export recorded by its text alone once replayed a tree the real model never saw, and the
    evals passed while every live flamegraph arrived as metadata. Only a text-only answer is its text.
    """
    structured = answer.get("structuredContent")
    if structured is None:
        return text_of(answer)
    return json.dumps(structured, ensure_ascii=False, separators=(",", ":"))


def record_workload(java: str, work_dir: Path, seconds: int) -> dict[str, Path]:
    work_dir.mkdir(parents=True, exist_ok=True)
    files = recorded_files(work_dir)
    for path in files.values():
        path.unlink(missing_ok=True)
    for mode in ("baseline", "candidate"):
        command = [java, "-cp", tracing_class_path(), f"-XX:StartFlightRecording={JFR_OPTIONS},filename={files[mode]}",
                   str(WORKLOAD.resolve()), mode, str(seconds)]
        if mode == "candidate":
            command.append(str(files["heap"]))
        subprocess.run(command, check=True, cwd=work_dir, stdout=subprocess.DEVNULL)
    return files


def tracing_class_path() -> str:
    missing = [str(jar) for jar in TRACING_JARS if not jar.is_file()]
    if missing:
        raise SystemExit(f"the workload's tracing jars are not built ({', '.join(missing)}); run: {TRACING_BUILD}")
    return ":".join(str(jar.resolve()) for jar in TRACING_JARS)


def import_profiles(jeffrey: Jeffrey, files: dict[str, Path], recorded: Recorded) -> Profiles:
    ids = {}
    for kind, path in files.items():
        answer = jeffrey.call("recordings_analyzeFile", {"path": str(path), "name": PROFILE_NAMES[kind], "force": True})
        final = jeffrey.finished(answer)
        ids[kind] = (final.get("structuredContent") or {}).get(PROFILE_ID) \
            or ((final.get("structuredContent") or {}).get("result") or {}).get(PROFILE_ID)
        if not ids[kind]:
            raise RuntimeError(f"no profileId after importing {path}: {text_of(final)[:400]}")
        if kind == "heap":
            # The one import a case repeats: the heap case names this file, so its answer is the mock.
            recorded.fixed["recordings_analyzeFile"] = seen_by_model(answer)
            recorded.inputs["recordings_analyzeFile"][()] = {"path": str(path)}
            operation = (answer.get("structuredContent") or {}).get(OPERATION_ID)
            if operation:
                recorded.answers["operations_status"][(operation,)] = seen_by_model(final)
                recorded.inputs["operations_status"][(operation,)] = {OPERATION_ID: operation}
    profiles = Profiles(ids["baseline"], ids["candidate"], ids["heap"])
    jeffrey.finished(jeffrey.call("heap_prepare", {PROFILE_ID: profiles.heap}))
    # The answer a case gets is the one at rest: every other heap mock is recorded from the prepared dump,
    # so a heap_prepare recorded while it was still running would tell the model to poll a preparation
    # that, in every other answer, has already finished.
    recorded.answers["heap_prepare"][(profiles.heap,)] = seen_by_model(
        jeffrey.call("heap_prepare", {PROFILE_ID: profiles.heap}))
    recorded.inputs["heap_prepare"][(profiles.heap,)] = {PROFILE_ID: profiles.heap}
    return profiles


def seed_calls(tools: list[dict], profiles: Profiles) -> list[tuple[str, dict]]:
    """The calls the cases lean on first, then each tool whose required arguments are a profile, a
    profile pair or an enum's values."""
    pairs = [(profiles.candidate, profiles.baseline), (profiles.baseline, profiles.candidate)]
    calls = [("jvm_exceptionSites", {PROFILE_ID: profile, "framePrefixes": APPLICATION_PACKAGES})
             for profile in (profiles.baseline, profiles.candidate)]
    for tool in ("compare_movements", "compare_flamegraph"):
        for event_type, weighted in COMPARED_EVENT_TYPES:
            calls += [(tool, {PROFILE_ID: primary, BASELINE_PROFILE_ID: baseline, "eventType": event_type,
                              **({"useWeight": True} if weighted else {})}) for primary, baseline in pairs]
    for tool in tools:
        name, schema = tool["name"], tool.get("inputSchema") or {}
        if name in NOT_CALLED_FROM_ANSWERS or name == "operations_status":
            continue
        required = [argument for argument in schema.get("required", [])]
        free = [argument for argument in required if argument not in (PROFILE_ID, BASELINE_PROFILE_ID)
                and not (schema["properties"][argument].get("enum"))]
        if free:
            continue
        combinations = [{}]
        if BASELINE_PROFILE_ID in required:
            combinations = [{PROFILE_ID: primary, BASELINE_PROFILE_ID: baseline} for primary, baseline in pairs]
        elif PROFILE_ID in required:
            combinations = [{PROFILE_ID: profile} for profile in profiles.all]
        for argument in required:
            values = schema["properties"][argument].get("enum")
            if values:
                combinations = [{**combination, argument: value} for combination in combinations for value in values]
        calls += [(name, combination) for combination in combinations]
    return calls


def offered_calls(structured: dict) -> list[tuple[str, dict]]:
    """Every ready call an answer offers, wherever in it the server put one."""
    calls = []

    def walk(node):
        if isinstance(node, dict):
            if isinstance(node.get("tool"), str) and isinstance(node.get("arguments"), dict):
                calls.append((node["tool"], node["arguments"]))
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(structured)
    return calls


def crawl(jeffrey: Jeffrey, tools: list[dict], profiles: Profiles, recorded: Recorded) -> None:
    schemas = {tool["name"]: tool.get("inputSchema") or {} for tool in tools}
    queue = collections.deque(seed_calls(tools, profiles))
    seen, calls = set(), 0
    while queue and calls < MAX_CALLS:
        tool, arguments = queue.popleft()
        if tool not in schemas or tool in NOT_CALLED_FROM_ANSWERS:
            continue
        required = schemas[tool].get("required", [])
        if any(argument not in arguments for argument in required):
            continue
        key = tuple(str(arguments[argument]) for argument in sorted(required))
        identity = json.dumps([tool, arguments], sort_keys=True)
        if identity in seen or (key and len(recorded.answers.get(tool, {})) >= MAX_FIXTURES_PER_TOOL):
            continue
        seen.add(identity)
        calls += 1
        try:
            answer = jeffrey.call(tool, arguments)
        except RuntimeError as error:
            print(f"  skipped {tool} {arguments}: {error}", file=sys.stderr)
            continue
        if answer.get("isError"):
            continue
        if not key:
            if tool not in recorded.fixed:
                recorded.fixed[tool] = seen_by_model(answer)
                recorded.inputs[tool][()] = arguments
        else:
            # Keyed by every required value; write_mocks decides which can name a file.
            if key not in recorded.answers[tool]:
                recorded.answers[tool][key] = seen_by_model(answer)
                recorded.inputs[tool][key] = arguments
        queue.extend(offered_calls(answer.get("structuredContent") or {}))
    print(f"recorded from {calls} calls", file=sys.stderr)


def addressed(required: list[str], answers: dict[tuple, str]) -> list[str] | None:
    """The required arguments a fixture's file name is built from, or None when they cannot tell its answers apart.

    An argument whose every recorded value can be part of a file name addresses the fixture. One that cannot —
    an operation's name, "POST /checkout" — is pinned instead, by the mock's `expect:` list of the values recorded,
    which the harness enforces by aborting a run that passes another; that works only while the addressing
    arguments still tell each answer apart, and only for text copied from an answer: free text with many values
    (a class name per object) fails the first, a query the agent writes (`COMPOSED_ARGUMENTS`) the second, and
    both stay unmocked.
    """
    named = [argument for index, argument in enumerate(required)
             if all(FILE_NAME_SAFE.fullmatch(key[index]) for key in answers)]
    if COMPOSED_ARGUMENTS & (set(required) - set(named)):
        return None
    projections = {tuple(key[required.index(argument)] for argument in named) for key in answers}
    return named if len(projections) == len(answers) else None


def write_mocks(tools: list[dict], recorded: Recorded) -> None:
    shutil.rmtree(MOCKS, ignore_errors=True)
    FIXTURES.mkdir(parents=True)
    schemas = {tool["name"]: tool.get("inputSchema") or {} for tool in tools}
    for tool, text in sorted(recorded.fixed.items()):
        guard = ""
        if tool == "recordings_analyzeFile":
            path = recorded.inputs[tool][()]["path"]
            guard = "expect:\n  path: " + json.dumps(path) + "\n"
        (MOCKS / f"{tool}.md").write_text(f"---\ntype: fixed\n{guard}---\n\n{text}\n")
    # A tool none of whose calls could be keyed gets no mock, so the runs do not offer it at all
    # rather than offering one that answers every call with a missing fixture.
    answered = {}
    for tool, answers in sorted(recorded.answers.items()):
        required = sorted(schemas[tool].get("required", []))
        named = addressed(required, answers)
        if named is None:
            answers = {key: text for key, text in answers.items() if all(map(FILE_NAME_SAFE.fullmatch, key))}
            named = addressed(required, answers)
        if not answers:
            continue
        answered[tool] = answers
        recorded.inputs[tool] = {key: recorded.inputs[tool][key] for key in answers}
        placeholders = "-".join(f"{{input.{argument}}}" for argument in named)
        expect = "".join(f"  {argument}: " + json.dumps(sorted({key[required.index(argument)] for key in answers}))
                         + "\n" for argument in required if argument not in named)
        (MOCKS / f"{tool}.md").write_text("---\ntype: fixed\n" + (f"expect:\n{expect}" if expect else "")
                                          + f"---\n\n{{{{file:fixtures/{tool}-{placeholders}.txt}}}}\n")
        for key, text in answers.items():
            name = "-".join(value for argument, value in zip(required, key) if argument in named)
            (FIXTURES / f"{tool}-{name}.txt").write_text(text + "\n")
    mocked = set(recorded.fixed) | set(answered)
    listed = [tool for tool in tools if tool["name"] in mocked]
    (MOCKS / "_tools.json").write_text(json.dumps({"tools": listed}, indent=1, ensure_ascii=False) + "\n")
    inputs = [{"tool": tool, "arguments": arguments}
              for tool in sorted(mocked) for arguments in recorded.inputs[tool].values()]
    (MOCKS / "_inputs.json").write_text(json.dumps(inputs, indent=1) + "\n")
    write_contract_graders(listed, inputs)
    print(f"wrote {len(mocked)} mocked tools, {sum(map(len, answered.values()))} fixtures", file=sys.stderr)


# Fixed mocks have neither optional/default substitutions nor conditional dispatch: a fixture is
# addressed by the required arguments alone, so it answers a call whatever its optional ones say.
# For most of them that is still the right answer — a different size, order, page or level of detail
# of the same figures, or a filter that picks rows the fixture holds anyway. A few change what the
# figures *are*: the weighting, the window, the frames left out or credited, a computation asked for,
# the matches a search adds. Only those are graded, by a deterministic mock_calls grader that fails
# the run when one differs from every recorded call. It is a post-call failure, not a router: the
# fixture was already returned when the grader rejects the run.
SEMANTIC_OPTIONS = {"useWeight", "startEpochMs", "endEpochMs", "excludeIdle", "excludeNonJava", "threadMode",
                    "bucketMs", "framePrefixes", "includeJvmInternal", "compute"}
# A search elsewhere selects rows; on a flamegraph it adds the matches and their share.
SEMANTIC_OPTIONS_OF = {"flamegraph_export": {"search"}}
WEIGHT_DEFAULTS = {"jdk.ExecutionSample": False, "jdk.ObjectAllocationSample": True,
                   "jdk.JavaMonitorEnter": True}
WEIGHT_DEFAULT_TOOLS = {"flamegraph_export", "compare_flamegraph", "compare_movements"}
FALSE_DEFAULTS = {"excludeIdle", "excludeNonJava", "threadMode", "includeJvmInternal", "compute", "retry", "errorsOnly"}
NO_DEFAULT = object()


def json_pattern(value) -> str:
    """A JSON literal, allowing serializer whitespace but retaining its value and array order."""
    if isinstance(value, list):
        return r"\[\s*" + r"\s*,\s*".join(json_pattern(item) for item in value) + r"\s*\]"
    if isinstance(value, float):
        # Python and JSON.stringify choose different spellings of the same number.
        encoded = json.dumps(value)
        plain = format(Decimal(str(value)), "f")
        if "." in plain:
            plain = plain.rstrip("0").rstrip(".")
        if value == 0:
            plain = "0"
        exponent = re.sub(r"e([+-])0+(\d+)", r"e\1\2", encoded)
        return "(?:" + "|".join(re.escape(part) for part in dict.fromkeys((encoded, plain, exponent))) + ")"
    return re.escape(json.dumps(value, ensure_ascii=False, separators=(",", ":")))


def semantic_options(tool: str, schema: dict) -> list[str]:
    required = set(schema.get("required", []))
    graded = SEMANTIC_OPTIONS | SEMANTIC_OPTIONS_OF.get(tool, set())
    return sorted(name for name in schema.get("properties", {}) if name in graded and name not in required)


def server_default(tool: str, name: str, prop: dict, arguments: dict):
    """The value the server uses when the option is left out, where it is known; else NO_DEFAULT."""
    if name == "useWeight" and tool in WEIGHT_DEFAULT_TOOLS:
        return WEIGHT_DEFAULTS.get(arguments.get("eventType"), NO_DEFAULT)
    if name == "useWeight" and tool == "timeline_hotWindows":
        return False
    if name in FALSE_DEFAULTS:
        return False
    return prop["default"] if "default" in prop else NO_DEFAULT


def input_pattern(tool: str, schema: dict, arguments: dict) -> str:
    """A regex for the input objects the recorded call answers: each answer-changing option holds the
    recorded value, or is left out where that value is the server's default; anything else is free."""
    # The required arguments name the fixture, and decide defaults such as an event type's weighting,
    # so an option is held to the recorded call with the same ones.
    checks = [r'(?=[^{}\n]*"' + re.escape(name) + r'"\s*:\s*' + json_pattern(arguments[name]) + r"(?=\s*[,}]))"
              for name in sorted(set(schema.get("required", [])) & set(arguments))]
    for name in semantic_options(tool, schema):
        prop = schema["properties"][name]
        key = r'[^{}\n]*"' + re.escape(name) + r'"\s*:'
        absent = "(?!" + key + ")"
        default = server_default(tool, name, prop, arguments)
        value = arguments.get(name, default)
        if value is NO_DEFAULT:
            checks.append(absent)
            continue
        present = "(?=" + key + r"\s*" + json_pattern(value) + r"(?=\s*[,}]))"
        checks.append("(?:" + absent + "|" + present + ")" if value == default else present)
    return r"\{" + "".join(checks) + r"[^\n]*\}"


def write_contract_graders(tools: list[dict], inputs: list[dict]) -> None:
    by_tool = collections.defaultdict(list)
    for entry in inputs:
        by_tool[entry["tool"]].append(entry["arguments"])
    rejected = []
    for tool in tools:
        name, schema = tool["name"], tool.get("inputSchema") or {}
        if not semantic_options(name, schema):
            continue
        # Import replay is deliberately a snapshot of this file, with no writer state machine.
        # Its path is guarded in the responder; name/force do not model a new import in this suite.
        if name == "recordings_analyzeFile":
            continue
        accepted = "|".join(input_pattern(name, schema, args) for args in by_tool[name]) or r"(?!)"
        rejected.append(r'^\{"tool"\s*:\s*"mcp__[^"\n]*__' + re.escape(name)
                        + r'"\s*,\s*"input"\s*:(?!\s*(?:' + accepted + r'))')
    pattern = "|".join(rejected) or r"(?!)"
    grader = ("---\n# Generated by record-plugin-eval-mocks.py from mocks/jeffrey/_inputs.json.\n"
              "type: regex\ntarget: mock_calls\nmatch: not_contains\nflags: m\npattern: "
              + json.dumps(pattern) + "\n---\n")
    for case in sorted(MOCKED_CASES.iterdir()):
        if case.is_dir() and (case / "graders").is_dir():
            (case / "graders/recorded-call-semantics.md").write_text(grader)


def measure_figures(jeffrey: Jeffrey, profiles: Profiles) -> dict[str, int]:
    figures = {}
    for figure in FIGURES:
        answer = jeffrey.call(figure.tool, {PROFILE_ID: getattr(profiles, figure.profile), "query": figure.query})
        figures[figure.name] = int(answer["structuredContent"]["rows"][0][0])
    return figures


def figure_pattern(value: int) -> str:
    """How a grader matches a count, with or without its thousands separator: 1446 is '1[,.\\s ]?446'."""
    digits = str(value)
    if len(digits) <= 3:
        return digits
    return f"{digits[:-3]}[,.\\s ]?{digits[-3:]}"


def rename_in_cases(profiles: Profiles, figures: dict[str, int]) -> None:
    """A grader that names a profile names it by id, and a new recording gives each profile a new one;
    a live case's grader names a figure of the recording, which a new recording measures anew."""
    if not RECORDED_PROFILES.exists():
        return
    previous = json.loads(RECORDED_PROFILES.read_text())
    renames = {previous[kind]: getattr(profiles, kind) for kind in ("baseline", "candidate", "heap")}
    for name, value in (previous.get("figures") or {}).items():
        # A count under a thousand has no separator to anchor its pattern, and would rename any number.
        if name in figures and value >= 1000:
            renames[figure_pattern(value)] = figure_pattern(figures[name])
    for path in (path for cases in CASES for path in cases.rglob("*.md")):
        text = path.read_text()
        renamed = text
        for old, new in renames.items():
            renamed = renamed.replace(old, new)
        if renamed != text:
            path.write_text(renamed)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--url", required=True)
    parser.add_argument("--work-dir", required=True, type=Path)
    parser.add_argument("--java", default="java")
    parser.add_argument("--seconds", type=int, default=30)
    parser.add_argument("--load", action="store_true",
                        help="import the recorded files into an empty Jeffrey for the live cases, and record nothing")
    parser.add_argument("--reuse", action="store_true",
                        help="record the mocks from the files already in --work-dir (a clone of the recordings "
                             "repository) instead of running the workload again")
    options = parser.parse_args()
    jeffrey = Jeffrey(options.url)
    catalogue = (jeffrey.call("profiles_list", {}).get("structuredContent") or {}).get("profiles") or []
    if catalogue:
        # Every answer that lists profiles would list those too, with nothing recorded behind them.
        print(f"{options.url} already holds {len(catalogue)} profile(s): record against an empty Jeffrey "
              f"(a fresh jeffrey.microscope.home.dir)", file=sys.stderr)
        return 1
    if options.load:
        return load(jeffrey, options.work_dir.resolve())
    tools = jeffrey.tools()
    if options.reuse:
        files = verified_files(options.work_dir.resolve())
        if files is None:
            return 1
    else:
        files = record_workload(options.java, options.work_dir.resolve(), options.seconds)
    recorded = Recorded()
    profiles = import_profiles(jeffrey, files, recorded)
    print(f"profiles: {profiles}", file=sys.stderr)
    crawl(jeffrey, tools, profiles, recorded)
    # The graders are renamed before the mocks are written, so the call-contract graders written
    # with the mocks carry the new ids and are not renamed a second time.
    figures = measure_figures(jeffrey, profiles)
    rename_in_cases(profiles, figures)
    write_mocks(tools, recorded)
    RECORDED_PROFILES.write_text(json.dumps(
        {"baseline": profiles.baseline, "candidate": profiles.candidate, "heap": profiles.heap,
         "heapDumpPath": str(files["heap"]), "figures": figures,
         "files": {path.name: sha256_of(path) for path in files.values()}}, indent=1) + "\n")
    return 0


def recorded_files(work_dir: Path) -> dict[str, Path]:
    return {"baseline": work_dir / "checkout-baseline.jfr", "candidate": work_dir / "checkout-candidate.jfr",
            "heap": work_dir / "checkout.hprof"}


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def unpack_heap_dump(work_dir: Path) -> None:
    """The recordings are kept with the dump gzipped (RECORDINGS_REPO); the import reads it unpacked."""
    dump = recorded_files(work_dir)["heap"]
    packed = dump.with_name(dump.name + GZIP_SUFFIX)
    if not dump.exists() and packed.exists():
        with gzip.open(packed, "rb") as source, dump.open("wb") as target:
            shutil.copyfileobj(source, target)


def verified_files(work_dir: Path) -> dict[str, Path] | None:
    """The recording the mocks were taken from, as a recording left it in work_dir or a clone of the
    recordings repository holds it; None, with the reason printed, when a file is missing or is not
    that recording (its checksum)."""
    unpack_heap_dump(work_dir)
    files = recorded_files(work_dir)
    missing = [str(path) for path in files.values() if not path.exists()]
    if missing:
        print(f"--load and --reuse take the files a recording left in --work-dir, or a clone of "
              f"{RECORDINGS_REPO}; missing: {missing}", file=sys.stderr)
        return None
    sums = json.loads(RECORDED_PROFILES.read_text()).get("files") or {}
    wrong = [path.name for path in files.values() if path.name in sums and sha256_of(path) != sums[path.name]]
    if wrong:
        print(f"these files are not the recording the mocks were taken from: {wrong} (see {RECORDINGS_REPO})",
              file=sys.stderr)
        return None
    return files


def load(jeffrey: Jeffrey, work_dir: Path) -> int:
    """The live cases' Jeffrey: the recordings the mocks were taken from, imported under the same names,
    refused when they are not the files the mocks were recorded from (their checksums) or do not answer
    the figures the graders name."""
    files = verified_files(work_dir)
    if files is None:
        return 1
    recorded = json.loads(RECORDED_PROFILES.read_text())
    profiles = import_profiles(jeffrey, files, Recorded())
    expected = recorded.get("figures") or {}
    measured = measure_figures(jeffrey, profiles)
    if measured != expected:
        print(f"these files are not the recording the graders name: figures {measured}, expected {expected}",
              file=sys.stderr)
        return 1
    print(f"loaded {profiles}; figures {measured} as the graders name them", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
