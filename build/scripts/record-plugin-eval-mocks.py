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
   `candidate` with its deliberate regression and a heap dump at the end;
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

Usage: record-plugin-eval-mocks.py --url http://localhost:8585/api/mcp --work-dir /tmp/jeffrey-eval
                                   [--java /path/to/java] [--seconds 30]
"""

import argparse
import collections
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
MOCKS = PLUGIN_DIR / "evals/mocks/jeffrey"
FIXTURES = MOCKS / "fixtures"
RECORDED_PROFILES = PLUGIN_DIR / "evals/fixtures/recorded-profiles.json"
CASES = PLUGIN_DIR / "evals/cases"

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

# The workload's own package, the way analyze-exceptions passes it, so the recorded throw sites name
# their own frame — the first answer recorded for a key is the one a case gets.
APPLICATION_PACKAGES = ["com.acme.checkout."]
# The comparisons the compare and regression cases read, in both directions: compare_list offers one
# event type, and a case may ask about any of these.
COMPARED_EVENT_TYPES = (("jdk.ExecutionSample", False), ("jdk.ObjectAllocationSample", True),
                        ("jdk.JavaMonitorEnter", True))


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


def record_workload(java: str, work_dir: Path, seconds: int) -> dict[str, Path]:
    work_dir.mkdir(parents=True, exist_ok=True)
    files = {"baseline": work_dir / "checkout-baseline.jfr", "candidate": work_dir / "checkout-candidate.jfr",
             "heap": work_dir / "checkout.hprof"}
    for path in files.values():
        path.unlink(missing_ok=True)
    for mode in ("baseline", "candidate"):
        command = [java, f"-XX:StartFlightRecording={JFR_OPTIONS},filename={files[mode]}",
                   str(WORKLOAD.resolve()), mode, str(seconds)]
        if mode == "candidate":
            command.append(str(files["heap"]))
        subprocess.run(command, check=True, cwd=work_dir, stdout=subprocess.DEVNULL)
    return files


def import_profiles(jeffrey: Jeffrey, files: dict[str, Path], recorded: Recorded) -> Profiles:
    names = {"baseline": "checkout baseline", "candidate": "checkout candidate", "heap": "checkout heap dump"}
    ids = {}
    for kind, path in files.items():
        answer = jeffrey.call("recordings_analyzeFile", {"path": str(path), "name": names[kind], "force": True})
        final = jeffrey.finished(answer)
        ids[kind] = (final.get("structuredContent") or {}).get(PROFILE_ID) \
            or ((final.get("structuredContent") or {}).get("result") or {}).get(PROFILE_ID)
        if not ids[kind]:
            raise RuntimeError(f"no profileId after importing {path}: {text_of(final)[:400]}")
        if kind == "heap":
            # The one import a case repeats: the heap case names this file, so its answer is the mock.
            recorded.fixed["recordings_analyzeFile"] = text_of(answer)
            recorded.inputs["recordings_analyzeFile"][()] = {"path": str(path)}
            operation = (answer.get("structuredContent") or {}).get(OPERATION_ID)
            if operation:
                recorded.answers["operations_status"][(operation,)] = text_of(final)
                recorded.inputs["operations_status"][(operation,)] = {OPERATION_ID: operation}
    profiles = Profiles(ids["baseline"], ids["candidate"], ids["heap"])
    jeffrey.finished(jeffrey.call("heap_prepare", {PROFILE_ID: profiles.heap}))
    # The answer a case gets is the one at rest: every other heap mock is recorded from the prepared dump,
    # so a heap_prepare recorded while it was still running would tell the model to poll a preparation
    # that, in every other answer, has already finished.
    recorded.answers["heap_prepare"][(profiles.heap,)] = text_of(
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
                recorded.fixed[tool] = text_of(answer)
                recorded.inputs[tool][()] = arguments
        elif all(FILE_NAME_SAFE.fullmatch(value) for value in key):
            if key not in recorded.answers[tool]:
                recorded.answers[tool][key] = text_of(answer)
                recorded.inputs[tool][key] = arguments
        queue.extend(offered_calls(answer.get("structuredContent") or {}))
    print(f"recorded from {calls} calls", file=sys.stderr)


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
    answered = {tool: answers for tool, answers in recorded.answers.items() if answers}
    for tool, answers in sorted(answered.items()):
        required = sorted(schemas[tool].get("required", []))
        placeholders = "-".join(f"{{input.{argument}}}" for argument in required)
        (MOCKS / f"{tool}.md").write_text(f"---\ntype: fixed\n---\n\n{{{{file:fixtures/{tool}-{placeholders}.txt}}}}\n")
        for key, text in answers.items():
            (FIXTURES / f"{tool}-{'-'.join(key)}.txt").write_text(text + "\n")
    mocked = set(recorded.fixed) | set(answered)
    listed = [tool for tool in tools if tool["name"] in mocked]
    (MOCKS / "_tools.json").write_text(json.dumps({"tools": listed}, indent=1, ensure_ascii=False) + "\n")
    inputs = [{"tool": tool, "arguments": arguments}
              for tool in sorted(mocked) for arguments in recorded.inputs[tool].values()]
    (MOCKS / "_inputs.json").write_text(json.dumps(inputs, indent=1) + "\n")
    write_contract_graders(listed, inputs)
    print(f"wrote {len(mocked)} mocked tools, {sum(map(len, answered.values()))} fixtures", file=sys.stderr)


# Fixed mocks have neither optional/default substitutions nor conditional dispatch. Keep their
# supported file addressing, and reject unsupported inputs with a deterministic mock_calls grader.
# This is a post-call score failure: it does not pretend the fixed responder is a semantic router.
WEIGHT_DEFAULTS = {"jdk.ExecutionSample": False, "jdk.ObjectAllocationSample": True,
                   "jdk.JavaMonitorEnter": True}
WEIGHT_DEFAULT_TOOLS = {"flamegraph_export", "compare_flamegraph", "compare_movements"}
FALSE_DEFAULTS = {"excludeIdle", "excludeNonJava", "threadMode", "includeJvmInternal", "compute", "retry", "errorsOnly"}


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


def input_pattern(tool: str, schema: dict, arguments: dict) -> str:
    required = set(schema.get("required", []))
    fields, present = {}, set(arguments)
    for name, value in arguments.items():
        fields[name] = json_pattern(value)
    # Only proven server defaults get omission aliases. An unsupported option is rejected even if
    # it happens not to change this particular answer. No fabricated weighted/window fixtures.
    for name, prop in schema.get("properties", {}).items():
        default = prop.get("default")
        has_default = "default" in prop
        if name in FALSE_DEFAULTS:
            default, has_default = False, True
        if name == "detail" and tool == "flamegraph_export":
            default, has_default = "STANDARD", True
        if name == "useWeight" and tool == "timeline_hotWindows":
            default, has_default = False, True
        if name == "useWeight" and tool in WEIGHT_DEFAULT_TOOLS and arguments.get("eventType") in WEIGHT_DEFAULTS:
            default, has_default = WEIGHT_DEFAULTS[arguments["eventType"]], True
        if has_default and name not in required:
            if name not in arguments:
                fields[name] = json_pattern(default)
            elif arguments[name] == default:
                present.discard(name)
    lookaheads = "".join(r'(?=[^{}\n]*"' + re.escape(name) + r'"\s*:\s*' + fields[name] + r'(?=\s*[,}]))'
                         for name in sorted(present))
    entries = [json_pattern(name) + r"\s*:\s*" + value for name, value in sorted(fields.items())]
    if not entries:
        return r"\{\s*\}"
    entry = "(?:" + "|".join(entries) + ")"
    return r"\{" + lookaheads + r"\s*(?:" + entry + r"(?:\s*,\s*" + entry + r")*)?\s*\}"


def write_contract_graders(tools: list[dict], inputs: list[dict]) -> None:
    by_tool = collections.defaultdict(list)
    for entry in inputs:
        by_tool[entry["tool"]].append(entry["arguments"])
    rejected = []
    for tool in tools:
        name, schema = tool["name"], tool.get("inputSchema") or {}
        if not (set(schema.get("properties", {})) - set(schema.get("required", []))):
            continue
        # Import replay is deliberately a snapshot of this file, with no writer state machine.
        # Its path is guarded in the responder; name/force do not model a new import in this suite.
        if name == "recordings_analyzeFile":
            continue
        accepted = "|".join(input_pattern(name, schema, args) for args in by_tool[name]) or r"(?!)"
        rejected.append(r'^\{"tool"\s*:\s*"mcp__[^"\n]*__' + re.escape(name)
                        + r'"\s*,\s*"input"\s*:\s*(?!(?:' + accepted + r')(?=\s*[,}]))')
    pattern = "|".join(rejected) or r"(?!)"
    grader = ("---\n# Generated by record-plugin-eval-mocks.py from mocks/jeffrey/_inputs.json.\n"
              "type: regex\ntarget: mock_calls\nmatch: not_contains\nflags: m\npattern: "
              + json.dumps(pattern) + "\n---\n")
    for case in sorted(CASES.iterdir()):
        if case.is_dir() and (case / "graders").is_dir():
            (case / "graders/recorded-call-semantics.md").write_text(grader)


def rename_profiles_in_cases(profiles: Profiles) -> None:
    """A grader that names a profile names it by id, and a new recording gives each profile a new one."""
    if not RECORDED_PROFILES.exists():
        return
    previous = json.loads(RECORDED_PROFILES.read_text())
    renames = {previous[kind]: getattr(profiles, kind) for kind in ("baseline", "candidate", "heap")}
    for path in CASES.rglob("*.md"):
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
    options = parser.parse_args()
    jeffrey = Jeffrey(options.url)
    catalogue = (jeffrey.call("profiles_list", {}).get("structuredContent") or {}).get("profiles") or []
    if catalogue:
        # Every answer that lists profiles would list those too, with nothing recorded behind them.
        print(f"{options.url} already holds {len(catalogue)} profile(s): record against an empty Jeffrey "
              f"(a fresh jeffrey.microscope.home.dir)", file=sys.stderr)
        return 1
    tools = jeffrey.tools()
    files = record_workload(options.java, options.work_dir.resolve(), options.seconds)
    recorded = Recorded()
    profiles = import_profiles(jeffrey, files, recorded)
    print(f"profiles: {profiles}", file=sys.stderr)
    crawl(jeffrey, tools, profiles, recorded)
    rename_profiles_in_cases(profiles)
    write_mocks(tools, recorded)
    RECORDED_PROFILES.write_text(json.dumps(
        {"baseline": profiles.baseline, "candidate": profiles.candidate, "heap": profiles.heap,
         "heapDumpPath": str(files["heap"])}, indent=1) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
