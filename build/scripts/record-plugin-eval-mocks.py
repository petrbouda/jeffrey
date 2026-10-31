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
   that answered, keyed by its required arguments — at most
   `MAX_FIXTURES_PER_TOOL` answers a tool, the first reached —
   (`{{file:fixtures/<tool>-{input.a}-….json}}`), and `_tools.json`, the real `tools/list` for those
   tools, so each mocked tool carries its real description and input schema.

A tool whose required argument takes free text that cannot be part of a file name (a SQL query, a
URI) gets no mock and is absent from the eval runs; so are the writers this script does not call.
The answers describe this Jeffrey's build — re-record after a change to what the tools return.

The SQL cases (`evals-live/`) run against a real Jeffrey instead, which `--load` prepares: it imports
the files a recording left in `--work-dir` into an empty Jeffrey, under the names the mocks carry, and
checks the figures those cases' graders name against it. A recording measures those figures
(`FIGURES`) and rewrites them in the graders, as it does the profile ids.

Usage: record-plugin-eval-mocks.py --url http://localhost:8585/api/mcp --work-dir /tmp/jeffrey-eval
                                   [--java /path/to/java] [--seconds 30] [--load]
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
from pathlib import Path

PLUGIN_DIR = Path("jeffrey-claude-plugin")
WORKLOAD = PLUGIN_DIR / "evals/fixtures/workload/com/acme/checkout/Checkout.java"
MOCKS = PLUGIN_DIR / "evals/mocks/jeffrey"
FIXTURES = MOCKS / "fixtures"
RECORDED_PROFILES = PLUGIN_DIR / "evals/fixtures/recorded-profiles.json"
CASES = (PLUGIN_DIR / "evals/cases", PLUGIN_DIR / "evals-live/cases")

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
    files = recorded_files(work_dir)
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
            recorded.fixed["recordings_analyzeFile"] = text_of(answer)
    profiles = Profiles(ids["baseline"], ids["candidate"], ids["heap"])
    jeffrey.finished(jeffrey.call("heap_prepare", {PROFILE_ID: profiles.heap}))
    # The answer a case gets is the one at rest: every other heap mock is recorded from the prepared dump,
    # so a heap_prepare recorded while it was still running would tell the model to poll a preparation
    # that, in every other answer, has already finished.
    recorded.answers["heap_prepare"][(profiles.heap,)] = text_of(
        jeffrey.call("heap_prepare", {PROFILE_ID: profiles.heap}))
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
        if name in NOT_CALLED_FROM_ANSWERS:
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
            recorded.fixed.setdefault(tool, text_of(answer))
        elif all(FILE_NAME_SAFE.fullmatch(value) for value in key):
            recorded.answers[tool].setdefault(key, text_of(answer))
        queue.extend(offered_calls(answer.get("structuredContent") or {}))
    print(f"recorded from {calls} calls", file=sys.stderr)


def write_mocks(tools: list[dict], recorded: Recorded) -> None:
    shutil.rmtree(MOCKS, ignore_errors=True)
    FIXTURES.mkdir(parents=True)
    schemas = {tool["name"]: tool.get("inputSchema") or {} for tool in tools}
    for tool, text in sorted(recorded.fixed.items()):
        (MOCKS / f"{tool}.md").write_text(f"---\ntype: fixed\n---\n\n{text}\n")
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
    print(f"wrote {len(mocked)} mocked tools, {sum(map(len, answered.values()))} fixtures", file=sys.stderr)


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
    files = record_workload(options.java, options.work_dir.resolve(), options.seconds)
    recorded = Recorded()
    profiles = import_profiles(jeffrey, files, recorded)
    print(f"profiles: {profiles}", file=sys.stderr)
    crawl(jeffrey, tools, profiles, recorded)
    write_mocks(tools, recorded)
    figures = measure_figures(jeffrey, profiles)
    rename_in_cases(profiles, figures)
    RECORDED_PROFILES.write_text(json.dumps(
        {"baseline": profiles.baseline, "candidate": profiles.candidate, "heap": profiles.heap,
         "heapDumpPath": str(files["heap"]), "figures": figures}, indent=1) + "\n")
    return 0


def recorded_files(work_dir: Path) -> dict[str, Path]:
    return {"baseline": work_dir / "checkout-baseline.jfr", "candidate": work_dir / "checkout-candidate.jfr",
            "heap": work_dir / "checkout.hprof"}


def load(jeffrey: Jeffrey, work_dir: Path) -> int:
    """The live cases' Jeffrey: the recordings the mocks were taken from, imported under the same names,
    refused when their figures are not the ones the graders name."""
    files = recorded_files(work_dir)
    missing = [str(path) for path in files.values() if not path.exists()]
    if missing:
        print(f"--load imports the files a recording left in --work-dir; missing: {missing}", file=sys.stderr)
        return 1
    profiles = import_profiles(jeffrey, files, Recorded())
    expected = json.loads(RECORDED_PROFILES.read_text()).get("figures") or {}
    measured = measure_figures(jeffrey, profiles)
    if measured != expected:
        print(f"these files are not the recording the graders name: figures {measured}, expected {expected}",
              file=sys.stderr)
        return 1
    print(f"loaded {profiles}; figures {measured} as the graders name them", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
