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
mock under `jeffrey-agent-plugin/evals/mocks/jeffrey/`, and a tool with no mock is not offered at
all. Hand-written answers would test the skills against a server that does not exist, so this script
takes them from one that does:

1. records the eval workload (`evals/fixtures/workload`) under JFR twice — `baseline`, then
   `candidate` with its deliberate regression and a heap dump at the end — with jeffrey-events and
   jeffrey-tracing on its class path (`TRACING_JARS`, built from `utilities/`), so the recordings carry
   sampled traces; and the order history (`OrderHistory.java`, an N+1 through the service's own JDBC
   wrapper) once, kept at `ORDERS_RECORDING`;
2. imports the four files into the Jeffrey at `--url` with `recordings_analyzeFile`, as a user would,
   and prepares the heap dump with `heap_prepare`;
3. calls every tool the way a model following the skills would reach it: each tool whose required
   arguments are only a profile (or a profile pair, or one of an enum's values), for every profile,
   then every call an answer offers in `followUp.nextTools`, `investigationAreas[].nextTools` or
   `topFindings[].nextTool`, and the calls each row of a listing stands for (`ROW_CALLS`: every traced
   operation's slowest traces and export), breadth first;
4. rewrites the profile ids the cases' graders name to the new ones, and writes one mock per tool
   that answered, addressed by its required arguments and graded against its full input — at most
   `MAX_FIXTURES_PER_TOOL` answers a tool, the first reached —
   (`{{file:fixtures/<tool>-{input.a}-….json}}`), and `_tools.json`, the real `tools/list` for those
   tools, so each mocked tool carries its real description and input schema.

A tool whose required argument takes free text that cannot be part of a file name (an operation's name,
a URI), with more than one value a profile, is answered by a relay instead (`write_relay_mock`): a
`type: agent` mock whose fixture holds every answer recorded for the profile, which the judge model hands
back verbatim for the call that matches. A query the agent writes itself (a SQL query) gets no mock and is
absent from the eval runs; so are the writers this script does not call.
The answers describe this Jeffrey's build — re-record after a change to what the tools return.

The SQL cases (`evals-live/`) run against a real Jeffrey instead, which `--load` prepares: it imports
the files a recording left in `--work-dir` (or the copies kept in this repository: the checkout runs and the
dump gzipped beside the tests' recordings, the order history and the persons modes under build/plugin-evals/,
the persons pair in shared/test) into an empty Jeffrey, under the names the
mocks carry — the two checkout runs at the commits the live scaffold history gives them
(`liveCommits`) — and checks the figures those cases' graders name against it. A recording measures those figures
(`FIGURES`) and rewrites them in the graders, as it does the profile ids.

Usage: record-plugin-eval-mocks.py --url http://localhost:8585/api/mcp --work-dir /tmp/jeffrey-eval
                                   [--java /path/to/java] [--seconds 30] [--load | --reuse [--record-missing]]
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

from plugin_layout import PLUGIN_DIR as PLUGIN_PATH

PLUGIN_DIR = Path(PLUGIN_PATH)
WORKLOAD = PLUGIN_DIR / "evals/fixtures/workload/com/acme/checkout/Checkout.java"
# The second workload: the service's order history, whose every request runs one statement per order (an N+1),
# recorded once as the `orders` profile. Its recording is kept in this repository (ORDERS_RECORDING): --reuse
# and --load copy it into --work-dir when it is not there, as they do every kept recording (KEPT_RECORDINGS).
ORDERS_WORKLOAD = PLUGIN_DIR / "evals/fixtures/workload/com/acme/checkout/OrderHistory.java"
ORDERS_RECORDING = Path("build/plugin-evals/order-history.jfr")
# The real service: two runs of jeffrey-testapp-server, a persons REST service with a database, profiled under
# load by the provisioner and kept in this repository for the tests (TestappRecording): writing each person
# out directly, then through a JSON tree. Not recorded by this script — imported as they are.
PERSONS_RESOURCES = Path("shared/test/src/main/resources/cafe/jeffrey/test/recording/testapp")
PERSONS_RECORDINGS = {"personsDirect": PERSONS_RESOURCES / "direct/rec-20261103-191259+0000.jfr",
                      "personsDom": PERSONS_RESOURCES / "dom/rec-20261103-191259+0000.jfr"}
# The same service in its planted-problem modes (testapp.mode n-plus-one, lock, exceptions), recorded with the
# JDK's JFR (profile settings, monitors and parks from 1 ms, every exception thrown) under the testapp's own
# client and kept gzipped in this repository beside the order history: n-plus-one and exceptions 60 s on one
# core under one client; lock 30 s on two cores under six clients' worth of load, the concurrency at which a
# monitor held across the database call is what the requests wait on.
MODE_RECORDINGS = {"personsNPlusOne": Path("build/plugin-evals/persons-n-plus-one.jfr.gz"),
                   "personsLock": Path("build/plugin-evals/persons-lock.jfr.gz"),
                   "personsExceptions": Path("build/plugin-evals/persons-exceptions.jfr.gz")}
# The checkout runs and their heap dump, kept beside the tests' recordings (CheckoutRecording): the files the
# mocks were first recorded from, byte for byte, the dump gzipped.
CHECKOUT_RESOURCES = Path("shared/test/src/main/resources/cafe/jeffrey/test/recording/checkout")
KEPT_RECORDINGS = {"baseline": CHECKOUT_RESOURCES / "checkout-baseline.jfr",
                   "candidate": CHECKOUT_RESOURCES / "checkout-candidate.jfr",
                   "heap": CHECKOUT_RESOURCES / "checkout.hprof.gz",
                   "orders": ORDERS_RECORDING, **PERSONS_RECORDINGS, **MODE_RECORDINGS}
ORDERS_SECONDS = 20
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
GZIP_SUFFIX = ".gz"
MOCKED_CASES = PLUGIN_DIR / "evals/cases"
# The repository the relative paths above are read against when the recorder is run from elsewhere (its tests).
REPOSITORY = Path(__file__).resolve().parents[2]
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
# The order history's own pass (crawl): a handful of seeds and what their answers offer.
ORDERS_MAX_CALLS = 400
# The persons service's own pass: every profile-only tool on both runs, the comparison both ways, the code tools
# on its methods, and what their answers offer.
PERSONS_MAX_CALLS = 3500
# The persons pass reads five runs where the others read one or two: each tool keeps this many times its
# fixtures there, so the modes' answers are not crowded out by the provisioner pair's.
PERSONS_FIXTURE_SCALE = 5
# Breadth first, so the answers kept are the ones nearest the seeds: a heap walk offers a path, the
# referrers and the children of every object it names, and a model reads the first few.
MAX_FIXTURES_PER_TOOL = 8

PROFILE_NAMES = {"baseline": "checkout baseline", "candidate": "checkout candidate", "heap": "checkout heap dump",
                 "orders": "order history", "personsDirect": "persons direct", "personsDom": "persons dom",
                 "personsNPlusOne": "persons n-plus-one", "personsLock": "persons lock",
                 "personsExceptions": "persons exceptions"}
PROFILE_KINDS = tuple(PROFILE_NAMES)
# The profiles this script records by running a workload; the persons runs are kept, never re-recorded.
WORKLOAD_KINDS = ("baseline", "candidate", "heap", "orders")
# The commits the live suite's scaffold history gives the two checkout runs (`liveCommits` in RECORDED_PROFILES,
# checked against the scaffold by test-plugin-eval-mocks.py): --load imports each run at its commit, so the live
# cases read `recordingCommit` against a repository that holds it.
LIVE_COMMITS = "liveCommits"

PROFILE_ID = "profileId"
BASELINE_PROFILE_ID = "baselineProfileId"
OPERATION_ID = "operationId"

# The writers are recorded only where this script calls them on purpose — the imports and the heap
# preparation — never from a nextTools entry, which could change the state the other answers describe.
NOT_CALLED_FROM_ANSWERS = {
    "recordings_analyzeFile", "recordings_analyzeRecording", "recordings_delete", "heap_prepare",
    "connector_download", "connector_fetchFile", "operations_cancel", "ide_link", "ide_open",
}
FILE_NAME_SAFE = re.compile(r"[A-Za-z0-9._-]+")
# Text the agent writes itself rather than copies from an answer — a SQL or OQL query. A mock pinned to the
# queries this script happened to run would abort every run that asks its own, so such a tool stays unmocked.
COMPOSED_ARGUMENTS = {"query"}

# The workload's own package, the way analyze-exceptions passes it, so the recorded throw sites name
# their own frame — the first answer recorded for a key is the one a case gets.
APPLICATION_PACKAGES = ["com.acme.checkout."]
# The other roots a model may read off the scaffolded repository for the same code — the shared-code section asks
# for the shortest package root of two or three segments, and the workload's one package gives `com.acme.` as
# readily. Each seeded call that takes framePrefixes is repeated with them, and accepted with the recorded
# fixture only when its answer carries the same figures (`equivalent`).
EQUIVALENT_PACKAGES = (["com.acme."],)
# The comparisons the compare and regression cases read, in both directions: compare_list offers one
# event type, and a case may ask about any of these.
COMPARED_EVENT_TYPES = (("jdk.ExecutionSample", False), ("jdk.ObjectAllocationSample", True),
                        ("jdk.JavaMonitorEnter", True))
# The workload's methods a code case asks about, as a developer reading Checkout.java names them: the class in
# source spelling (a nested class with a dot — a `$` cannot name a fixture) and the method.
WORKLOAD_METHODS = (("com.acme.checkout.Checkout.PriceCalculator", "formatLine"),
                    ("com.acme.checkout.Checkout.PriceCalculator", "reprice"),
                    ("com.acme.checkout.Checkout.PriceCalculator", "taxOf"),
                    ("com.acme.checkout.Checkout.SessionCache", "touch"),
                    ("com.acme.checkout.Checkout.SessionCache", "busyWait"),
                    ("com.acme.checkout.Checkout.CouponParser", "discount"),
                    ("com.acme.checkout.Checkout.AuditLog", "record"),
                    ("com.acme.checkout.Checkout", "checkout"))
# Optional arguments that address a fixture beside the required ones, because they pick what the answer is about:
# `code_method` answers one method of the class. A call without one is not answered (a missing file-name segment
# is a tool error): code_method on a whole class has no mock.
ADDRESSING_OPTIONS = {"code_method": ("methodName",)}
# The order history's statements: Jeffrey's JDBC query event, the one statement event that carries a stack.
JDBC_QUERY = "jeffrey.JdbcQuery"
# The event types with stacks a call-site case groups: the monitor the workload contends on.
CALL_SITE_EVENT_TYPES = ("jdk.JavaMonitorEnter",)
# The persons service's own package and the methods its code cases ask about, as its sources name them.
PERSONS_PACKAGES = ["jeffrey.testapp."]
PERSONS_EQUIVALENT_PACKAGES = (["jeffrey.testapp.server."],)
PERSONS_METHODS = (("jeffrey.testapp.server.PersonController", "toJsonTree"),
                   ("jeffrey.testapp.server.PersonController", "getNPersons"),
                   ("jeffrey.testapp.server.PersonController", "getRandomPerson"),
                   ("jeffrey.testapp.server.service.InefficientPersonService", "getRandomPerson"),
                   ("jeffrey.testapp.server.service.InefficientPersonService", "getNPersons"),
                   ("jeffrey.testapp.server.PersonRepository", "findById"),
                   ("jeffrey.testapp.server.PersonRepository", "findByIds"),
                   ("jeffrey.testapp.server.PersonRepository", "count"),
                   ("jeffrey.testapp.server.PersonRepository", "modifyOpinion"),
                   ("jeffrey.testapp.server.service.NPlusOnePersonService", "getNPersons"),
                   ("jeffrey.testapp.server.service.LockPersonService", "getRandomPerson"),
                   ("jeffrey.testapp.server.service.LockPersonService", "getNPersons"),
                   ("jeffrey.testapp.server.service.ExceptionsPersonService", "normalizePhone"),
                   ("jeffrey.testapp.server.service.ExceptionsPersonService", "getNPersons"))
# The persons runs' samples as async-profiler took them: CPU, allocation (TLAB, weighted by bytes), the monitors
# and the wall clock — compared both ways and charged to the service's own code.
PERSONS_COMPARED_EVENT_TYPES = (("jdk.ExecutionSample", False), ("jdk.ObjectAllocationInNewTLAB", True),
                                ("jdk.JavaMonitorEnter", True), ("profiler.WallClockSample", False))
# The modes' graphs a case reads (JDK-only recordings: CPU, allocation in TLABs, monitors, thrown exceptions)
# and the endpoints whose traces hold their problems.
MODE_GRAPHED_EVENT_TYPES = (("jdk.ExecutionSample", False), ("jdk.ObjectAllocationInNewTLAB", True),
                            ("jdk.JavaMonitorEnter", True), ("jdk.JavaExceptionThrow", False))
HTTP_SERVER_EXCHANGE = "jeffrey.HttpServerExchange"
PERSONS_OPERATIONS = ("GET /persons/{count}", "GET /persons")
# The persons runs' event types a code case groups by call site: its statements and its monitors.
PERSONS_CALL_SITE_EVENT_TYPES = (JDBC_QUERY, "jdk.JavaMonitorEnter")
# A tool asked about more subjects than MAX_FIXTURES_PER_TOOL keeps: each workload's method, on both of its recordings;
# each traced operation and HTTP endpoint of every profile (`ROW_CALLS`), and the slowest traces each operation names.
MAX_OPERATION_FIXTURES = 64
# The first SLOWEST_EXPORTED traces of every operation a profile's listing names, with their span frames.
MAX_TRACE_FIXTURES = 64
MAX_FIXTURES_OF = {"code_method": 2 * max(len(WORKLOAD_METHODS), len(PERSONS_METHODS)) + MAX_FIXTURES_PER_TOOL,
                   "traces_slowestTraces": MAX_OPERATION_FIXTURES, "traces_operationExport": MAX_OPERATION_FIXTURES,
                   "traces_traceExport": MAX_TRACE_FIXTURES, "traces_spanFlamegraphExport": MAX_TRACE_FIXTURES,
                   "http_endpoint": MAX_OPERATION_FIXTURES}


@dataclass(frozen=True)
class RowCalls:
    """The calls each row of an answer stands for: `tools`, called with the answer's profile and the row's `fields`;
    with `first`, only for that many rows from the top."""
    rows: str
    tools: tuple[str, ...]
    fields: tuple[str, ...]
    first: int | None = None


# An answer that lists subjects offers its follow-ups for the first one only, and a model asks about the one its
# question is about: every operation's slowest traces and export, and every endpoint's dashboard, are recorded.
# A slowest-traces answer offers the export of its slowest trace, and a model often reads the next one or two
# instead — the export of each of the first SLOWEST_EXPORTED traces is recorded, so the one it picks is answered.
SLOWEST_EXPORTED = 3
ROW_CALLS = {"traces_operations": RowCalls("operations", ("traces_slowestTraces", "traces_operationExport"),
                                           ("name", "kind", "eventType")),
             "traces_slowestTraces": RowCalls("traces", ("traces_traceExport",), ("traceId",), SLOWEST_EXPORTED),
             "http_overview": RowCalls("endpoints", ("http_endpoint",), ("uri",))}
# The arguments a relay's fixture is chosen by, where a tool takes them: its answers are kept one file per profile.
RELAY_FILE_ARGUMENTS = (PROFILE_ID, BASELINE_PROFILE_ID)
# What a relay asks the real server for, in place of every value no fixture file can be named by, to record the
# answer a call naming none of the recorded ones gets.
UNRECORDED_VALUE = "(not recorded)"
RELAY_ANSWER = re.compile(r"<answer>\n(.*?)\n</answer>", re.S)
# The tools a relay also answers by their answer-changing options (`SEMANTIC_OPTIONS`), where a variant was recorded
# whose figures differ — the wall clock with idle threads left out. Their answers are small enough for the judge to
# hand back verbatim; a flamegraph (up to MAX_CHARS) is not, so flamegraph_export keeps its fixed mock and its other
# variants stay rejected by the call-contract grader.
OPTION_RELAYED_TOOLS = ("code_hotspots", "compare_movements", "code_callSites")
# The longest answer a relay hands back: the judge copies it verbatim, which holds for the small answers (the largest
# relayed today is about 20,000 characters) and not for a flamegraph. A tool with a longer recorded answer is not
# relayed: an operation's flamegraph (traces_operationFlamegraphExport) keeps the first answer per profile
# (first_per_address), an option relay its fixed mock.
RELAY_MAX_ANSWER_CHARS = 25_000
WALL_CLOCK = "profiler.WallClockSample"

# What may differ between two answers that address one fixture while the figures stay the same: the follow-up
# and how the own packages were chosen. A call reached with other graded options is accepted with the
# recorded fixture only when, these left out, its answer is the fixture's (`equivalent`).
PROVENANCE_FIELDS = {"followUp", "framePrefixesSource", "framePrefixes", "suggestedFramePrefixes"}


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
# The persons service's statements by name, as the graders of its live cases name them.
PERSONS_STATEMENTS = "SELECT COUNT(*) FROM events WHERE event_type = 'jeffrey.JdbcQuery' AND (fields->>'name') = '{name}'"
FIGURES = (
    Figure("orders", "heap_executeQuery", "heap", f"SELECT COUNT(*) {ORDERS_COUPONS}"),
    Figure("promoOrders", "heap_executeQuery", "heap", f"SELECT COUNT(*) {ORDERS_COUPONS} AND s.content LIKE 'SPRING-%'"),
    # count-queries: the COUNT statements of the DOM run, against its 7,161 count requests.
    Figure("domCountQueries", "jfr_executeQuery", "personsDom", PERSONS_STATEMENTS.format(name="count_query")),
)


@dataclass(frozen=True)
class Profiles:
    baseline: str
    candidate: str
    heap: str
    orders: str
    personsDirect: str
    personsDom: str
    personsNPlusOne: str
    personsLock: str
    personsExceptions: str

    @property
    def all(self) -> tuple[str, ...]:
        """The profiles every profile-only tool is seeded with: the checkout runs. The order history gets the
        calls its cases make (`orders_seed_calls`), so the crawl's budget for the checkout answers stays."""
        return self.baseline, self.candidate, self.heap


@dataclass
class Variant:
    """An answer recorded under a key another answer already holds, with the inputs that ask for it."""
    inputs: list[dict]
    answer: str


@dataclass
class Recorded:
    """Each tool's answers by the values of its required arguments, in the order they were made."""
    answers: dict[str, dict[tuple, str]] = field(default_factory=lambda: collections.defaultdict(dict))
    fixed: dict[str, str] = field(default_factory=dict)
    inputs: dict[str, dict[tuple, dict]] = field(default_factory=lambda: collections.defaultdict(dict))
    # Other inputs a fixture answers as it answers its own: same figures, different provenance.
    aliases: dict[str, dict[tuple, list[dict]]] = field(
        default_factory=lambda: collections.defaultdict(lambda: collections.defaultdict(list)))
    # Other answers recorded under a fixture's key, asked with other options and holding other figures: kept for an
    # OPTION_RELAYED_TOOLS tool only, each with the inputs it answers.
    variants: dict[str, dict[tuple, list["Variant"]]] = field(
        default_factory=lambda: collections.defaultdict(lambda: collections.defaultdict(list)))
    # A relayed tool's answer to a call naming none of the recorded values, by its relay file: the input and the error.
    unmatched: dict[str, dict[tuple, tuple[dict, str]]] = field(default_factory=lambda: collections.defaultdict(dict))


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


def record_workload(java: str, work_dir: Path, seconds: int, kinds: tuple[str, ...] = WORKLOAD_KINDS) -> dict[str, Path]:
    """Runs the workloads behind `kinds` under JFR into work_dir; the heap dump comes with the candidate run."""
    work_dir.mkdir(parents=True, exist_ok=True)
    files = recorded_files(work_dir)
    for kind in kinds:
        files[kind].unlink(missing_ok=True)
    for mode in ("baseline", "candidate"):
        if mode not in kinds:
            continue
        command = [java, "-cp", tracing_class_path(), f"-XX:StartFlightRecording={JFR_OPTIONS},filename={files[mode]}",
                   str(WORKLOAD.resolve()), mode, str(seconds)]
        if mode == "candidate":
            command.append(str(files["heap"]))
        subprocess.run(command, check=True, cwd=work_dir, stdout=subprocess.DEVNULL)
    if "orders" in kinds:
        command = [java, "-cp", tracing_class_path(),
                   f"-XX:StartFlightRecording={JFR_OPTIONS},filename={files['orders']}",
                   str(ORDERS_WORKLOAD.resolve()), str(ORDERS_SECONDS)]
        subprocess.run(command, check=True, cwd=work_dir, stdout=subprocess.DEVNULL)
        ORDERS_RECORDING.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(files["orders"], ORDERS_RECORDING)
    return files


def tracing_class_path() -> str:
    missing = [str(jar) for jar in TRACING_JARS if not jar.is_file()]
    if missing:
        raise SystemExit(f"the workload's tracing jars are not built ({', '.join(missing)}); run: {TRACING_BUILD}")
    return ":".join(str(jar.resolve()) for jar in TRACING_JARS)


def import_profiles(jeffrey: Jeffrey, files: dict[str, Path], recorded: Recorded,
                    commits: dict[str, str] | None = None) -> Profiles:
    """Imports each file under its profile name — at its commit, for the kinds `commits` names."""
    ids = {}
    for kind, path in files.items():
        arguments = {"path": str(path), "name": PROFILE_NAMES[kind], "force": True}
        if commits and kind in commits:
            arguments["commit"] = commits[kind]
        answer = jeffrey.call("recordings_analyzeFile", arguments)
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
    profiles = Profiles(**{kind: ids[kind] for kind in PROFILE_KINDS})
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
    calls += code_seed_calls(profiles)
    calls += [(tool, {**arguments, "framePrefixes": packages}) for tool, arguments in list(calls)
              if arguments.get("framePrefixes") == APPLICATION_PACKAGES for packages in EQUIVALENT_PACKAGES]
    calls += profile_only_calls(tools, profiles.all, pairs)
    return calls


def profile_only_calls(tools: list[dict], profiles: tuple[str, ...], pairs: list[tuple[str, str]]) -> list[tuple[str, dict]]:
    """Each tool whose required arguments are a profile, a profile pair or an enum's values, for each of them."""
    calls = []
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
            combinations = [{PROFILE_ID: profile} for profile in profiles]
        for argument in required:
            values = schema["properties"][argument].get("enum")
            if values:
                combinations = [{**combination, argument: value} for combination in combinations for value in values]
        calls += [(name, combination) for combination in combinations]
    return calls


def persons_seed_calls(tools: list[dict], profiles: Profiles) -> list[tuple[str, dict]]:
    """The persons service as its cases reach it: the throw sites and the code tools on its own package (each
    repeated with the equivalent package root), the comparison of the provisioner's two runs both ways — the
    DOM run as the one that changed first — then every profile-only tool on every persons run, the modes'
    included."""
    runs = (profiles.personsDom, profiles.personsDirect, profiles.personsNPlusOne, profiles.personsLock,
            profiles.personsExceptions)
    # Compared: the provisioner's pair. The modes ran elsewhere (a laptop, JDK-only), so each is read on its own.
    pairs = [(profiles.personsDom, profiles.personsDirect), (profiles.personsDirect, profiles.personsDom)]
    own = {"framePrefixes": PERSONS_PACKAGES}
    calls = [("jvm_exceptionSites", {PROFILE_ID: profile, **own}) for profile in runs]
    for tool in ("compare_movements", "compare_flamegraph"):
        for event_type, weighted in PERSONS_COMPARED_EVENT_TYPES:
            calls += [(tool, {PROFILE_ID: primary, BASELINE_PROFILE_ID: baseline, "eventType": event_type,
                              **({"useWeight": True} if weighted else {})}) for primary, baseline in pairs]
    calls += [("code_hotspots", {PROFILE_ID: profile, "eventType": event_type, **own})
              for profile in runs for event_type, _ in PERSONS_COMPARED_EVENT_TYPES]
    # Asked without the repository at hand, an agent lets Jeffrey suggest the packages (SUGGESTED): same figures
    # when its suggestion is the service's own, an alias of the fixture above.
    calls += [("code_hotspots", {PROFILE_ID: profile, "eventType": event_type})
              for profile in runs for event_type, _ in PERSONS_COMPARED_EVENT_TYPES]
    calls += [("code_method", {PROFILE_ID: profile, "className": class_name, "methodName": method, **own})
              for profile in runs for class_name, method in PERSONS_METHODS]
    calls += [("code_callSites", {PROFILE_ID: profile, "eventType": event_type, **own})
              for profile in runs for event_type in PERSONS_CALL_SITE_EVENT_TYPES]
    calls += [("code_runtimeSurface", {PROFILE_ID: profile, **own}) for profile in runs]
    # The wall clock with idle threads left out, as a latency question reads it — other figures than the whole wall
    # clock, recorded after it as a variant its relay answers too (OPTION_RELAYED_TOOLS) — and the call sites asked
    # without the repository at hand, as the hotspots are above.
    idle_out = {"eventType": WALL_CLOCK, "excludeIdle": True}
    calls += [("code_hotspots", {PROFILE_ID: profile, **idle_out, **own}) for profile in runs]
    calls += [("compare_movements", {PROFILE_ID: primary, BASELINE_PROFILE_ID: baseline, **idle_out})
              for primary, baseline in pairs]
    calls += [("code_callSites", {PROFILE_ID: profile, "eventType": event_type})
              for profile in runs for event_type in PERSONS_CALL_SITE_EVENT_TYPES]
    calls += [(tool, {**arguments, "framePrefixes": packages}) for tool, arguments in list(calls)
              if arguments.get("framePrefixes") == PERSONS_PACKAGES for packages in PERSONS_EQUIVALENT_PACKAGES]
    # What each mode's case reads first, seeded rather than left to the walk: its graphs, weighted as the
    # flamegraph weighs them by default, and the traces of the endpoints the problem sits in.
    modes = (profiles.personsNPlusOne, profiles.personsLock, profiles.personsExceptions)
    calls += [("flamegraph_export", {PROFILE_ID: profile, "eventType": event_type,
                                     **({"useWeight": True} if weighted else {})})
              for profile in modes for event_type, weighted in MODE_GRAPHED_EVENT_TYPES]
    # The DOM run's too, so the slow endpoint's answers are among those a relay holds — and, where a tool's answers
    # are too long to relay and one fixture per profile is all a mock can keep (first_per_address), the one kept.
    calls += [(tool, {PROFILE_ID: profile, "name": operation, "kind": "SERVER", "eventType": HTTP_SERVER_EXCHANGE})
              for profile in (profiles.personsDom, *modes) for operation in PERSONS_OPERATIONS
              for tool in ("traces_slowestTraces", "traces_operationExport")]
    calls += profile_only_calls(tools, runs, pairs)
    return calls


def code_seed_calls(profiles: Profiles) -> list[tuple[str, dict]]:
    """The code tools as the code skills call them — with the workload's own package as `framePrefixes`, so the
    answer a case gets is the one charged to the code in its repository."""
    own = {"framePrefixes": APPLICATION_PACKAGES}
    recordings = (profiles.candidate, profiles.baseline)
    calls = [("code_hotspots", {PROFILE_ID: profile, "eventType": event_type, **own})
             for profile in recordings for event_type, _ in COMPARED_EVENT_TYPES]
    calls += [("code_method", {PROFILE_ID: profile, "className": class_name, "methodName": method, **own})
              for profile in recordings for class_name, method in WORKLOAD_METHODS]
    calls += [("code_callSites", {PROFILE_ID: profile, "eventType": event_type, **own})
              for profile in recordings for event_type in CALL_SITE_EVENT_TYPES]
    calls += [("code_runtimeSurface", {PROFILE_ID: profile, **own}) for profile in recordings]
    return calls


def addressing(tool: str, schema: dict) -> list[str]:
    """The arguments a fixture is addressed by, sorted: the required ones and the tool's ADDRESSING_OPTIONS."""
    return sorted(set(schema.get("required", [])) | set(ADDRESSING_OPTIONS.get(tool, ())))


def orders_seed_calls(profiles: Profiles) -> list[tuple[str, dict]]:
    """The order history as its cases reach it: the summary, the statements and where they are issued, the
    runtime surface, the database dashboard and the traces — the equivalent package root repeated, as the
    checkout's seeds are."""
    profile = {PROFILE_ID: profiles.orders}
    own = {"framePrefixes": APPLICATION_PACKAGES}
    shorter = [{"framePrefixes": packages} for packages in EQUIVALENT_PACKAGES]
    return [("profiles_summary", profile),
            ("code_callSites", {**profile, "eventType": JDBC_QUERY, **own}),
            ("code_runtimeSurface", {**profile, **own}),
            *[(tool, {**profile, **extra, **variant}) for tool, extra in
              (("code_callSites", {"eventType": JDBC_QUERY}), ("code_runtimeSurface", {})) for variant in shorter],
            ("jdbc_overview", profile),
            ("traces_overview", profile)]


def without_provenance(node):
    if isinstance(node, dict):
        return {key: without_provenance(value) for key, value in node.items() if key not in PROVENANCE_FIELDS}
    if isinstance(node, list):
        return [without_provenance(value) for value in node]
    return node


def equivalent(recorded: str, answer: dict) -> bool:
    """Whether an answer carries the same figures as a recorded fixture, its provenance left out."""
    structured = answer.get("structuredContent")
    if structured is None:
        return False
    try:
        fixture = json.loads(recorded)
    except json.JSONDecodeError:
        return False
    return without_provenance(fixture) == without_provenance(structured)


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
    """The checkout profiles first, as the cases have always been recorded, then the order history and the
    persons service each in a pass of its own, so their answers never take the budget or a tool's fixtures
    from the checkout's."""
    crawl_from(jeffrey, tools, seed_calls(tools, profiles), recorded, MAX_CALLS)
    crawl_from(jeffrey, tools, orders_seed_calls(profiles), recorded, ORDERS_MAX_CALLS)
    crawl_from(jeffrey, tools, persons_seed_calls(tools, profiles), recorded, PERSONS_MAX_CALLS,
               fixture_scale=PERSONS_FIXTURE_SCALE)


def crawl_from(jeffrey: Jeffrey, tools: list[dict], seeds: list[tuple[str, dict]], recorded: Recorded,
               budget: int, fixture_scale: int = 1) -> None:
    """Breadth first from the seeds, at most `budget` calls and each tool's fixture limit (times
    `fixture_scale`) counted from what this pass found it holding."""
    schemas = {tool["name"]: tool.get("inputSchema") or {} for tool in tools}
    held = {tool: len(answers) for tool, answers in recorded.answers.items()}
    queue = collections.deque(seeds)
    seen, calls = set(), 0
    while queue and calls < budget:
        tool, arguments = queue.popleft()
        if tool not in schemas or tool in NOT_CALLED_FROM_ANSWERS:
            continue
        required = addressing(tool, schemas[tool])
        if any(argument not in arguments for argument in required):
            continue
        key = tuple(str(arguments[argument]) for argument in required)
        identity = json.dumps([tool, arguments], sort_keys=True)
        limit = MAX_FIXTURES_OF.get(tool, MAX_FIXTURES_PER_TOOL) * fixture_scale
        # The limit counts fixtures, so a call that would only be another input of a fixture already held — an
        # equivalent package root, a size or page of the same figures — is still made and kept as its alias.
        full = key and key not in recorded.answers.get(tool, {}) and len(recorded.answers.get(tool, {})) - held.get(tool, 0) >= limit
        if identity in seen or full:
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
            elif semantics(tool, schemas[tool], arguments) != semantics(tool, schemas[tool], recorded.inputs[tool][key]):
                if equivalent(recorded.answers[tool][key], answer):
                    recorded.aliases[tool][key].append(arguments)
                elif tool in OPTION_RELAYED_TOOLS:
                    add_variant(recorded.variants[tool][key], arguments, answer)
        queue.extend(offered_calls(answer.get("structuredContent") or {}))
        queue.extend(row_calls(tool, arguments, answer.get("structuredContent") or {}))
        queue.extend(statement_calls(tool, arguments, answer.get("structuredContent") or {}))
    print(f"recorded from {calls} calls", file=sys.stderr)


def add_variant(variants: list[Variant], arguments: dict, answer: dict) -> None:
    """Another answer for a key: an input of the variant whose figures it carries, or a variant of its own."""
    for variant in variants:
        if equivalent(variant.answer, answer):
            variant.inputs.append(arguments)
            return
    variants.append(Variant([arguments], seen_by_model(answer)))


def row_calls(tool: str, arguments: dict, structured: dict) -> list[tuple[str, dict]]:
    """The calls `ROW_CALLS` says each row of this answer stands for, on the profile the answer is about."""
    expansion = ROW_CALLS.get(tool)
    if expansion is None:
        return []
    profile = {name: arguments[name] for name in RELAY_FILE_ARGUMENTS if name in arguments}
    calls = []
    for row in (structured.get(expansion.rows) or [])[:expansion.first]:
        if all(isinstance(row.get(name), str) for name in expansion.fields):
            subject = {name: row[name] for name in expansion.fields}
            calls += [(each, {**profile, **subject}) for each in expansion.tools]
    return calls


# A model narrows a statement's call sites to the one it is about (filterField name, filterValue count_query): every
# statement a grouped answer lists is asked about once more on its own, a variant its relay answers.
MAX_FILTERED_STATEMENTS = 10
FILTER_FIELD, FILTER_VALUE = "filterField", "filterValue"


def statement_calls(tool: str, arguments: dict, structured: dict) -> list[tuple[str, dict]]:
    """For a code_callSites answer grouped by statement name, the same call narrowed to each statement it lists."""
    if tool != "code_callSites" or FILTER_FIELD in arguments or structured.get("groupBy") != STATEMENT_NAME_FIELD:
        return []
    values = list(dict.fromkeys(site.get("groupValue") for site in structured.get("sites") or []
                                if isinstance(site.get("groupValue"), str)))
    return [(tool, {**arguments, FILTER_FIELD: STATEMENT_NAME_FIELD, FILTER_VALUE: value})
            for value in values[:MAX_FILTERED_STATEMENTS]]


def addressed(required: list[str], answers: dict[tuple, str]) -> list[str] | None:
    """The required arguments a fixture's file name is built from, or None when they cannot tell its answers apart.

    An argument whose every recorded value can be part of a file name addresses the fixture. One that cannot —
    an operation's name, "POST /checkout" — is pinned instead, by the mock's `expect:` list of the values recorded,
    which the harness enforces by aborting a run that passes another; that works only while the addressing
    arguments still tell each answer apart, and only for text copied from an answer: free text with many values
    (an operation's name per operation) fails the first and goes to a relay (`relay_file_arguments`) when no fixed
    mock is left, a query the agent writes (`COMPOSED_ARGUMENTS`) fails the second and stays unmocked.
    """
    named = [argument for index, argument in enumerate(required)
             if all(FILE_NAME_SAFE.fullmatch(key[index]) for key in answers)]
    if COMPOSED_ARGUMENTS & (set(required) - set(named)):
        return None
    projections = {tuple(key[required.index(argument)] for argument in named) for key in answers}
    return named if len(projections) == len(answers) else None


def first_per_address(required: list[str], answers: dict[tuple, str]) -> dict[tuple, str]:
    """When the file-safe arguments cannot tell a tool's answers apart — two operations of one profile, whose
    names (`GET /persons/{count}`) cannot be part of a file name — and no relay answers the tool (its answers are
    too long to copy, RELAY_MAX_ANSWER_CHARS), the first answer recorded for each of their values, so the tool
    stays mocked: the others' values leave the mock's `expect:` list with them, and a call naming one aborts the
    run instead of the whole tool going unoffered. None (an empty dict) when a composed argument would be left to
    `expect:`."""
    safe = [argument for index, argument in enumerate(required)
            if all(FILE_NAME_SAFE.fullmatch(key[index]) for key in answers)]
    if COMPOSED_ARGUMENTS & (set(required) - set(safe)):
        return {}
    first = {}
    for key, text in answers.items():
        first.setdefault(tuple(key[required.index(argument)] for argument in safe), (key, text))
    return dict(first.values())


def relay_file_arguments(required: list[str], answers: dict[tuple, str]) -> list[str] | None:
    """For a tool no fixed mock can answer at all, the arguments its relay's fixture file is named by — the profile,
    where it takes one. None when a fixed mock answers it: all its answers (`addressed`), or those whose values can
    all be part of a file name, the rest left out as before; and None when a query the agent writes is among the
    values no file can be named by, which no recording answers."""
    if addressed(required, answers) is not None:
        return None
    if any(all(map(FILE_NAME_SAFE.fullmatch, key)) for key in answers):
        return None
    unnamed = unnamed_arguments(required, answers)
    if COMPOSED_ARGUMENTS & unnamed:
        return None
    return [argument for argument in RELAY_FILE_ARGUMENTS if argument in required and argument not in unnamed]


def unnamed_arguments(required: list[str], answers: dict[tuple, str]) -> set[str]:
    """The required arguments some recorded value of which cannot be part of a file name."""
    return {argument for index, argument in enumerate(required)
            if not all(FILE_NAME_SAFE.fullmatch(key[index]) for key in answers)}


def relay_file(required: list[str], file_arguments: list[str], key: tuple) -> tuple:
    return tuple(key[required.index(argument)] for argument in file_arguments)


def relayed_tools(schemas: dict[str, dict], recorded: Recorded) -> dict[str, list[str]]:
    """Each recorded tool a relay answers, with the arguments its fixture file is named by."""
    relayed = {}
    for tool, answers in recorded.answers.items():
        file_arguments = relay_file_arguments(addressing(tool, schemas[tool]), answers)
        if file_arguments is not None:
            relayed[tool] = file_arguments
    return relayed


def probe_unmatched(jeffrey: Jeffrey, tools: list[dict], recorded: Recorded) -> None:
    """A relayed tool's real answer to a call that names none of the recorded values: once per relay file, the first
    recorded input with each value no file name can hold replaced by UNRECORDED_VALUE. Only an error is kept — the
    answer the server gives a subject it does not hold."""
    schemas = {tool["name"]: tool.get("inputSchema") or {} for tool in tools}
    for tool, file_arguments in sorted(relayed_tools(schemas, recorded).items()):
        required = addressing(tool, schemas[tool])
        unnamed = unnamed_arguments(required, recorded.answers[tool])
        for key in recorded.answers[tool]:
            file = relay_file(required, file_arguments, key)
            if file in recorded.unmatched[tool]:
                continue
            probe = {name: UNRECORDED_VALUE if name in unnamed else value
                     for name, value in recorded.inputs[tool][key].items()}
            try:
                answer = jeffrey.call(tool, probe)
            except RuntimeError as error:
                print(f"  no unmatched answer for {tool} {probe}: {error}", file=sys.stderr)
                continue
            if answer.get("isError"):
                recorded.unmatched[tool][file] = (probe, text_of(answer))


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
    relays = relays_of(schemas, recorded)
    for tool, answers in sorted(recorded.answers.items()):
        required = addressing(tool, schemas[tool])
        if tool in relays:
            write_relay_mock(relays[tool])
            answered[tool] = answers
            continue
        named = addressed(required, answers)
        if named is None:
            safe = {key: text for key, text in answers.items() if all(map(FILE_NAME_SAFE.fullmatch, key))}
            named = addressed(required, safe) if safe else None
            answers = safe if named is not None else first_per_address(required, answers)
            if named is None and answers:
                named = addressed(required, answers)
        if not answers or named is None:
            continue
        answered[tool] = answers
        recorded.inputs[tool] = {key: recorded.inputs[tool][key] for key in answers}
        recorded.aliases[tool] = {key: recorded.aliases[tool][key] for key in answers if key in recorded.aliases[tool]}
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
    variants = {tool: recorded.variants[tool] for tool in relays if tool in recorded.variants}
    inputs = [{"tool": tool, "arguments": arguments}
              for tool in sorted(mocked) for arguments in recorded.inputs[tool].values()]
    inputs += [{"tool": tool, "arguments": variant.inputs[0]}
               for tool in sorted(variants) for _, kept in sorted(variants[tool].items()) for variant in kept]
    inputs += [{"tool": tool, "arguments": arguments, "equivalentTo": recorded.inputs[tool][key]}
               for tool in sorted(mocked) for key, aliases in sorted(recorded.aliases.get(tool, {}).items())
               for arguments in aliases]
    inputs += [{"tool": tool, "arguments": arguments, "equivalentTo": variant.inputs[0]}
               for tool in sorted(variants) for _, kept in sorted(variants[tool].items()) for variant in kept
               for arguments in variant.inputs[1:]]
    (MOCKS / "_inputs.json").write_text(json.dumps(inputs, indent=1) + "\n")
    write_contract_graders(listed, inputs)
    print(f"wrote {len(mocked)} mocked tools ({len(relays)} relayed), {sum(map(len, answered.values()))} fixtures",
          file=sys.stderr)


@dataclass(frozen=True)
class RelayEntry:
    """One recorded answer of a relay, with every input that asks for it: the recorded one and its aliases."""
    inputs: tuple[dict, ...]
    answer: str


@dataclass(frozen=True)
class Relay:
    """What a relay mock is written from: the arguments its fixture file is named by, the ones the judge matches the
    call on, and per fixture file the recorded answers, the value an option left out stands for, and the server's
    answer to a call that names nothing recorded."""
    tool: str
    file_arguments: tuple[str, ...]
    chosen_by: tuple[str, ...]
    files: dict[tuple, list[RelayEntry]]
    defaults: dict[tuple, dict]
    unmatched: dict[tuple, tuple[dict, str]]


def relays_of(schemas: dict[str, dict], recorded: Recorded) -> dict[str, Relay]:
    """Every tool a relay answers: one addressed by a value no file name can hold, and an OPTION_RELAYED_TOOLS tool
    with a variant recorded."""
    relays = {tool: operation_relay(tool, addressing(tool, schemas[tool]), file_arguments, recorded)
              for tool, file_arguments in relayed_tools(schemas, recorded).items()}
    for tool in OPTION_RELAYED_TOOLS:
        if tool in schemas and tool not in relays and any(recorded.variants.get(tool, {}).values()):
            relay = option_relay(tool, schemas[tool], recorded)
            if relay is not None:
                relays[tool] = relay
    return {tool: relay for tool, relay in relays.items() if copyable(relay)}


def copyable(relay: Relay) -> bool:
    """Whether every answer a relay holds is short enough for the judge to hand back verbatim."""
    longest = max(len(entry.answer) for entries in relay.files.values() for entry in entries)
    if longest > RELAY_MAX_ANSWER_CHARS:
        print(f"  not relayed: {relay.tool}, an answer of {longest} characters", file=sys.stderr)
        return False
    return True


def operation_relay(tool: str, required: list[str], file_arguments: list[str], recorded: Recorded) -> Relay:
    """A relay choosing by the values no file name can hold — an operation's name — one fixture per profile."""
    files = collections.defaultdict(list)
    for key, text in recorded.answers[tool].items():
        files[relay_file(required, file_arguments, key)].append(RelayEntry((recorded.inputs[tool][key],), text))
    return Relay(tool, tuple(file_arguments), tuple(argument for argument in required if argument not in file_arguments),
                 dict(files), {}, dict(recorded.unmatched.get(tool, {})))


def option_relay(tool: str, schema: dict, recorded: Recorded) -> Relay | None:
    """A relay choosing by the answer-changing options, one fixture per fixed mock's key; None when a key cannot name
    a file, which leaves the tool its fixed mock."""
    required = addressing(tool, schema)
    answers = recorded.answers[tool]
    if addressed(required, answers) != required:
        return None
    options = semantic_options(tool, schema)
    files, defaults = {}, {}
    for key, text in answers.items():
        primary = recorded.inputs[tool][key]
        files[key] = [RelayEntry((primary, *recorded.aliases[tool].get(key, [])), text)]
        files[key] += [RelayEntry(tuple(variant.inputs), variant.answer)
                       for variant in recorded.variants.get(tool, {}).get(key, [])]
        defaults[key] = {name: server_default(tool, name, schema["properties"][name], primary) for name in options}
    return Relay(tool, tuple(required), tuple(options), files, defaults, {})


RELAY_ABORT_WHEN = ("Never. A call that matches no recorded call is answered with a tool error, as the "
                    "instructions say; it does not end the run.")
RELAY_INSTRUCTIONS = """You stand in for the `{tool}` tool of a real Jeffrey. Every answer below is one that Jeffrey gave
when this suite was recorded; you hand back the one this call asks for, and you never compose one.

This call asks for: {call}.
{left_out}
Find the recorded call whose arguments are exactly these, character for character. The call's other arguments
(a limit, a page size, a level of detail) do not choose the answer. Reply with that recorded call's answer,
the text between its <answer> and </answer> lines, exactly as recorded: every character as written, with
nothing before or after it, no code fence, no comment, nothing reformatted, shortened or recomputed.

When no recorded call has these arguments, answer with a tool error: the recorded unmatched answer, if there is
one, with the call's values in place of its input's; otherwise one line saying that no recorded call has these
arguments, naming them.

{fixture}
"""
RELAY_LEFT_OUT = """An argument shown empty was left out of the call. Before comparing, put in its place the value the
line below gives a left-out argument, or "unset" where that line gives none: every recorded call lists each of
these arguments that way, so the call's filled-in arguments equal a recorded call's exactly or not at all.
"""
# How a relay writes an answer-changing option that was left out and has no default.
RELAY_UNSET = "unset"


def write_relay_mock(relay: Relay) -> None:
    """A `type: agent` mock for a tool no fixed fixture can answer every recorded call of: one fixture per relay file
    holding each recorded call's arguments and answer, and the judge model hands back the one whose arguments match
    the call's — verbatim, so the answer is still the real server's."""
    placeholders = "".join(f"-{{input.{argument}}}" for argument in relay.file_arguments)
    instructions = RELAY_INSTRUCTIONS.format(
        tool=relay.tool, call=", ".join(f"{argument} = {{{{input.{argument}}}}}" for argument in relay.chosen_by),
        left_out=RELAY_LEFT_OUT if relay.defaults else "", fixture=f"{{{{file:fixtures/{relay.tool}{placeholders}.txt}}}}")
    (MOCKS / f"{relay.tool}.md").write_text("---\ntype: agent\nabort_when: " + json.dumps(RELAY_ABORT_WHEN)
                                            + "\n---\n\n" + instructions)
    for file, entries in relay.files.items():
        sections = []
        defaults = relay.defaults.get(file)
        if defaults:
            given = {name: value for name, value in defaults.items() if value is not NO_DEFAULT}
            sections.append(f"Left out, an argument has the value: {json.dumps(given, ensure_ascii=False)}; "
                            f"one not named here has none.")
        for number, entry in enumerate(entries, start=1):
            asked = " or ".join(json.dumps(relay_arguments(relay, arguments, defaults), ensure_ascii=False)
                                for arguments in entry.inputs)
            sections.append(f"Recorded call {number}, arguments: {asked}\n<answer>\n{entry.answer}\n</answer>")
        unmatched = relay.unmatched.get(file)
        if unmatched is not None:
            probe, error = unmatched
            sections.append(f"Recorded unmatched answer, a tool error, to the input "
                            f"{json.dumps(relay_arguments(relay, probe), ensure_ascii=False)}\n<error>\n{error}\n</error>")
        name = "".join(f"-{value}" for value in file)
        (FIXTURES / f"{relay.tool}{name}.txt").write_text("\n\n".join(sections) + "\n")


def relay_arguments(relay: Relay, arguments: dict, defaults: dict | None = None) -> dict:
    """The arguments of a recorded call the judge matches on: as the call gave them, and — where the relay knows what
    a left-out one stands for — every one of them, the left-out ones filled in (RELAY_UNSET where none is known)."""
    if not defaults:
        return {argument: arguments[argument] for argument in relay.chosen_by if argument in arguments}
    return {argument: arguments.get(argument, RELAY_UNSET if defaults[argument] is NO_DEFAULT else defaults[argument])
            for argument in relay.chosen_by}


def relay_answers(text: str) -> list[str]:
    """The recorded answers a relay fixture holds, each as the model is handed it."""
    return RELAY_ANSWER.findall(text)


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
SEMANTIC_OPTIONS_OF = {"flamegraph_export": {"search"},
                       # How the events are grouped, and which are counted, are what a call site's figures are.
                       "code_callSites": {"groupBy", "filterField", "filterValue"},
                       # One sample kind, or every kind the profile recorded: a method's figures per kind.
                       "code_method": {"eventType"}}
# An answer-changing option a recorded call may leave out to be answered for every value at once, where the answer
# lists the values it covers: code_method without an event type reads each recorded sample kind, so a call naming
# one of the kinds its answer lists gets the same figures from that fixture. Each entry is the option, the list in
# the answer, and the field of a list item that names the value.
COVERED_BY_ANSWER = {"code_method": ("eventType", "kinds", "eventType")}
WEIGHT_DEFAULTS = {"jdk.ExecutionSample": False, "jdk.ObjectAllocationSample": True,
                   "jdk.JavaMonitorEnter": True}
WEIGHT_DEFAULT_TOOLS = {"flamegraph_export", "compare_flamegraph", "compare_movements", "code_hotspots",
                        "compare_methods"}
FALSE_DEFAULTS = {"excludeIdle", "excludeNonJava", "threadMode", "includeJvmInternal", "compute", "retry", "errorsOnly"}
# code_callSites groups a JDBC statement's call sites by its statement name when no groupBy is given (CodeSurfaceMcpTools'
# JDBC_TYPES and STATEMENT_NAME_FIELD), so a call naming that field asks for the recorded answer.
JDBC_EVENT_TYPES = {"jeffrey.JdbcQuery", "jeffrey.JdbcInsert", "jeffrey.JdbcUpdate", "jeffrey.JdbcDelete",
                    "jeffrey.JdbcExecute", "jeffrey.JdbcStream"}
STATEMENT_NAME_FIELD = "name"
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


def semantics(tool: str, schema: dict, arguments: dict) -> dict:
    """The answer-changing options of a call, each as the server reads it: given, or its known default."""
    return {name: arguments.get(name, server_default(tool, name, schema["properties"][name], arguments))
            for name in semantic_options(tool, schema)}


def semantic_options(tool: str, schema: dict) -> list[str]:
    required = set(addressing(tool, schema))
    graded = SEMANTIC_OPTIONS | SEMANTIC_OPTIONS_OF.get(tool, set())
    return sorted(name for name in schema.get("properties", {}) if name in graded and name not in required)


def server_default(tool: str, name: str, prop: dict, arguments: dict):
    """The value the server uses when the option is left out, where it is known; else NO_DEFAULT."""
    if name == "useWeight" and tool in WEIGHT_DEFAULT_TOOLS:
        return WEIGHT_DEFAULTS.get(arguments.get("eventType"), NO_DEFAULT)
    if name == "useWeight" and tool == "timeline_hotWindows":
        return False
    if name == "groupBy" and tool == "code_callSites":
        return STATEMENT_NAME_FIELD if arguments.get("eventType") in JDBC_EVENT_TYPES else NO_DEFAULT
    if name in FALSE_DEFAULTS:
        return False
    return prop["default"] if "default" in prop else NO_DEFAULT


# What may stand between an input object's `{` and one of its own keys: characters outside any string and other
# than braces, or whole JSON strings — a value may hold a brace (an operation named "GET /orders/{customer}"),
# and a key inside a nested object never matches.
SAME_OBJECT = r'(?:[^{}"\n]|"(?:[^"\\\n]|\\.)*")*'


def input_pattern(tool: str, schema: dict, arguments: dict, covered: tuple = ()) -> str:
    """A regex for the input objects the recorded call answers: each answer-changing option holds the
    recorded value, or is left out where that value is the server's default; anything else is free.
    `covered` are the values of the tool's COVERED_BY_ANSWER option the recorded answer holds, where the
    recorded call left that option out."""
    # The arguments that name the fixture — the required ones and the tool's addressing options — decide defaults
    # such as an event type's weighting, so an option is held to the recorded call with the same ones. An addressing
    # option the recorded call left out is left out by the call too: its fixture answers only the call without it.
    checks = []
    for name in addressing(tool, schema):
        key = SAME_OBJECT + r'"' + re.escape(name) + r'"\s*:'
        checks.append("(?=" + key + r"\s*" + json_pattern(arguments[name]) + r"(?=\s*[,}]))" if name in arguments
                      else "(?!" + key + ")")
    for name in semantic_options(tool, schema):
        prop = schema["properties"][name]
        key = SAME_OBJECT + r'"' + re.escape(name) + r'"\s*:'
        absent = "(?!" + key + ")"
        default = server_default(tool, name, prop, arguments)
        value = arguments.get(name, default)
        if value is NO_DEFAULT:
            values = covered if name not in arguments and COVERED_BY_ANSWER.get(tool, (None,))[0] == name else ()
            checks.append("(?:" + "|".join([absent] + ["(?=" + key + r"\s*" + json_pattern(each) + r"(?=\s*[,}]))"
                                                       for each in values]) + ")" if values else absent)
            continue
        present = "(?=" + key + r"\s*" + json_pattern(value) + r"(?=\s*[,}]))"
        checks.append("(?:" + absent + "|" + present + ")" if value == default else present)
    return r"\{" + "".join(checks) + r"[^\n]*\}"


def covered_values(tool: str, schema: dict, arguments: dict) -> tuple:
    """The values of the tool's COVERED_BY_ANSWER option a recorded call's fixed fixture answers for, when the call
    left that option out: read off the fixture, so only values the answer holds figures for. Empty otherwise."""
    if tool not in COVERED_BY_ANSWER:
        return ()
    option, rows, field = COVERED_BY_ANSWER[tool]
    names = addressing(tool, schema)
    if option in arguments or not all(name in arguments for name in names):
        return ()
    fixtures = FIXTURES if FIXTURES.is_absolute() else REPOSITORY / FIXTURES
    fixture = fixtures / (f"{tool}-" + "-".join(str(arguments[name]) for name in names) + ".txt")
    if not fixture.exists():
        return ()
    try:
        answer = json.loads(fixture.read_text())
    except json.JSONDecodeError:
        return ()
    return tuple(sorted({row[field] for row in answer.get(rows) or [] if isinstance(row, dict) and field in row}))


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
        accepted = "|".join(input_pattern(name, schema, args, covered_values(name, schema, args))
                            for args in by_tool[name]) or r"(?!)"
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
    renames = {previous[kind]: getattr(profiles, kind) for kind in PROFILE_KINDS if kind in previous}
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
    parser.add_argument("--record-missing", action="store_true",
                        help="with --reuse: run the workload of a recording that is neither in --work-dir nor kept "
                             "in this repository (the order history), instead of refusing")
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
        work_dir = options.work_dir.resolve()
        copy_kept_recordings(work_dir)
        if options.record_missing and not recorded_files(work_dir)["orders"].exists():
            record_workload(options.java, work_dir, options.seconds, kinds=("orders",))
        files = verified_files(work_dir)
        if files is None:
            return 1
    else:
        record_workload(options.java, options.work_dir.resolve(), options.seconds, kinds=WORKLOAD_KINDS)
        copy_kept_recordings(options.work_dir.resolve())
        files = recorded_files(options.work_dir.resolve())
    recorded = Recorded()
    profiles = import_profiles(jeffrey, files, recorded)
    print(f"profiles: {profiles}", file=sys.stderr)
    crawl(jeffrey, tools, profiles, recorded)
    probe_unmatched(jeffrey, tools, recorded)
    # The graders are renamed before the mocks are written, so the call-contract graders written
    # with the mocks carry the new ids and are not renamed a second time.
    figures = measure_figures(jeffrey, profiles)
    rename_in_cases(profiles, figures)
    write_mocks(tools, recorded)
    previous = json.loads(RECORDED_PROFILES.read_text()) if RECORDED_PROFILES.exists() else {}
    RECORDED_PROFILES.write_text(json.dumps(
        {**{kind: getattr(profiles, kind) for kind in PROFILE_KINDS}, "heapDumpPath": str(files["heap"]), "figures": figures,
         "files": {path.name: sha256_of(path) for path in files.values()},
         LIVE_COMMITS: previous.get(LIVE_COMMITS) or {}}, indent=1) + "\n")
    return 0


def recorded_files(work_dir: Path) -> dict[str, Path]:
    return {"baseline": work_dir / "checkout-baseline.jfr", "candidate": work_dir / "checkout-candidate.jfr",
            "heap": work_dir / "checkout.hprof", "orders": work_dir / "order-history.jfr",
            "personsDirect": work_dir / "persons-direct.jfr", "personsDom": work_dir / "persons-dom.jfr",
            "personsNPlusOne": work_dir / "persons-n-plus-one.jfr", "personsLock": work_dir / "persons-lock.jfr",
            "personsExceptions": work_dir / "persons-exceptions.jfr"}


def copy_kept_recordings(work_dir: Path) -> None:
    """Every recording the mocks are taken from is kept in this repository (KEPT_RECORDINGS); a work dir without
    one gets the kept copy — the gzipped ones (the dump, the persons modes) as they are, for unpack_kept."""
    files = recorded_files(work_dir)
    for kind, kept in KEPT_RECORDINGS.items():
        target = files[kind].with_name(files[kind].name + GZIP_SUFFIX) if kept.name.endswith(GZIP_SUFFIX) else files[kind]
        if not files[kind].exists() and not target.exists() and kept.exists():
            work_dir.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(kept, target)


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for block in iter(lambda: file.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def unpack_kept(work_dir: Path) -> None:
    """The dump and the persons modes are kept gzipped (KEPT_RECORDINGS); the import reads them unpacked."""
    for path in recorded_files(work_dir).values():
        packed = path.with_name(path.name + GZIP_SUFFIX)
        if not path.exists() and packed.exists():
            with gzip.open(packed, "rb") as source, path.open("wb") as target:
                shutil.copyfileobj(source, target)


def verified_files(work_dir: Path) -> dict[str, Path] | None:
    """The recording the mocks were taken from, as a recording left it in work_dir or a clone of the
    recordings repository holds it; None, with the reason printed, when a file is missing or is not
    that recording (its checksum)."""
    unpack_kept(work_dir)
    files = recorded_files(work_dir)
    missing = [str(path) for path in files.values() if not path.exists()]
    if missing:
        print(f"--load and --reuse take the files a recording left in --work-dir, or the copies kept in this "
              f"repository ({', '.join(sorted({str(path.parent) for path in KEPT_RECORDINGS.values()}))}; "
              f"--record-missing records the order history); missing: {missing}", file=sys.stderr)
        return None
    sums = json.loads(RECORDED_PROFILES.read_text()).get("files") or {}
    wrong = [path.name for path in files.values() if path.name in sums and sha256_of(path) != sums[path.name]]
    if wrong:
        print(f"these files are not the recording the mocks were taken from: {wrong}", file=sys.stderr)
        return None
    return files


def load(jeffrey: Jeffrey, work_dir: Path) -> int:
    """The live cases' Jeffrey: the recordings the mocks were taken from, imported under the same names,
    refused when they are not the files the mocks were recorded from (their checksums) or do not answer
    the figures the graders name."""
    copy_kept_recordings(work_dir)
    files = verified_files(work_dir)
    if files is None:
        return 1
    recorded = json.loads(RECORDED_PROFILES.read_text())
    commits = recorded.get(LIVE_COMMITS) or {}
    profiles = import_profiles(jeffrey, files, Recorded(), commits)
    expected = recorded.get("figures") or {}
    measured = measure_figures(jeffrey, profiles)
    if measured != expected:
        print(f"these files are not the recording the graders name: figures {measured}, expected {expected}",
              file=sys.stderr)
        return 1
    print(f"loaded {profiles} at commits {commits or 'none'}; figures {measured} as the graders name them",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
