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
"""Brings the Agent Plugin page's data up to date after a change to the MCP server or the plugin.

What it does, in order — each step only when the plan needs it:

1. Plan. The cases to run are the stale ones (`plugin-usage-map.py --stale`: a case whose skills, agents, routing,
   case files or recorded answers moved since its replay), or the ones `--cases` names, or every mocked case with
   `--all`. The mocks need re-recording when the server or the recorder changed since they were last recorded
   (`--rerecord auto`, read from git), and a re-recording gives every profile a new id, so it runs every case. The
   plan prints with each case's cost from its last replay, and stops there unless `--run` is given: runs cost
   model time.
2. Re-record (when planned). Builds the Microscope jar, boots it on an empty home, checks its tools/list against
   `build/mcp/tools-list.snapshot.json` (regenerated from this jar when it drifted), and records the mocks into the
   directory the heap cases name (`recorded-profiles.json`'s `heapDumpPath`).
3. Run. `claude plugin eval` for the planned cases, three runs each by default, with `--keep-temp` (the map reads
   the traces) and `--no-publish`, held to `--max-cost-usd` across all of them.
4. Map. Regenerates `jeffrey-pages/src/data/plugin-usage.json` with the version in
   `build/plugin/manifests/identity.json`, stamping each replay with the inputs it ran on — refused when the plugin
   changed while the runs were going, since the stamps would then describe files the runs never saw.
5. Check and report. The offline checks, then each case's runs and every failed grader with its reason; the kept
   run directories are removed afterwards unless `--keep-runs`.

The mocked suite only: the live cases (`evals-live/`) need a Jeffrey loaded with the recordings and run with
`--mocks off`, and are reported as stale until they are run that way.

Usage:
    python3 build/scripts/refresh-plugin-evals.py                 # the plan: what is stale, what it would cost
    python3 build/scripts/refresh-plugin-evals.py --run           # run it
    python3 build/scripts/refresh-plugin-evals.py --run --cases fix-it slow-endpoints
    python3 build/scripts/refresh-plugin-evals.py --run --all --rerecord yes
"""
import argparse
import importlib.util
import json
import os
import shutil
import socket
import stat
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path

from plugin_layout import PLUGIN_DIR

ROOT = Path(__file__).resolve().parents[2]
PLUGIN = ROOT / PLUGIN_DIR
SCRIPTS = ROOT / "build/scripts"
IDENTITY = ROOT / "build/plugin/manifests/identity.json"
RECORDED_PROFILES = PLUGIN / "evals/fixtures/recorded-profiles.json"
MOCKS = PLUGIN / "evals/mocks/jeffrey"
JAR = ROOT / "build/build-microscope/target/microscope.jar"
MOCKED = "mocked"
DEFAULT_RUNS = 3
DEFAULT_MAX_COST_USD = 25.0
# A replay's costUsd leaves out the judge's; this covers it in the estimate.
JUDGE_SHARE = 0.15
STARTUP_SECONDS = 180
EVAL_TEMP_PREFIX = "claude-eval-"
# What a change to means the recorded answers are no longer the server's: the server's code and the recorder.
ANSWER_SOURCES = ("jeffrey-microscope", "shared", "build/scripts/record-plugin-eval-mocks.py")
MAIN_SOURCES = "/src/main/"

spec = importlib.util.spec_from_file_location("usage_map", SCRIPTS / "plugin-usage-map.py")
usage_map = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = usage_map
spec.loader.exec_module(usage_map)


@dataclass(frozen=True)
class Plan:
    cases: list[str]
    rerecord: bool
    reasons: dict[str, str]
    estimate_usd: float


def git(*arguments: str) -> str:
    return subprocess.run(["git", *arguments], cwd=ROOT, capture_output=True, text=True, check=True).stdout


def answers_changed() -> list[str]:
    """The server and recorder files changed since the mocks were last recorded (committed or not). A shallow clone
    that does not hold the recording's commit sees no change: fetch the history (`fetch-depth: 0`) to tell."""
    recorded = git("log", "-1", "--format=%H", "--", str(MOCKS.relative_to(ROOT))).strip()
    changed = set(git("diff", "--name-only", recorded, "--", *ANSWER_SOURCES).split()) if recorded else set()
    changed |= {line[3:] for line in git("status", "--porcelain", "--", *ANSWER_SOURCES).splitlines()}
    return sorted(path for path in changed
                  if path.endswith("record-plugin-eval-mocks.py") or (MAIN_SOURCES in path and path.endswith(".java")))


def mocked_cases() -> list[str]:
    return sorted(prompt.parent.name for prompt in (PLUGIN / "evals/cases").glob("*/prompt.md"))


def last_costs(data: dict) -> dict[str, float]:
    return {replay["case"]: sum(run.get("costUsd") or 0 for run in replay["runs"]) / max(1, len(replay["runs"]))
            for replay in data.get("replays", []) if replay["suite"] == MOCKED}


def plan(options, data: dict) -> Plan:
    changed = answers_changed()
    rerecord = options.rerecord == "yes" or (options.rerecord == "auto" and bool(changed))
    stale = {entry["case"]: entry for entry in usage_map.stale_replays(PLUGIN, data) if entry["suite"] == MOCKED}
    if rerecord or options.all:
        cases = mocked_cases()
        why = (f"the mocks are re-recorded ({', '.join(changed[:3]) or 'asked for'}): every profile id changes"
               if rerecord else "--all")
        reasons = {case: why for case in cases}
    elif options.cases:
        unknown = sorted(set(options.cases) - set(mocked_cases()))
        if unknown:
            raise SystemExit(f"no mocked case named {', '.join(unknown)}")
        cases, reasons = sorted(options.cases), {case: "named with --cases" for case in options.cases}
    else:
        cases = sorted(stale)
        reasons = {case: "recorded before inputs were kept" if stale[case]["changed"] is None
                   else "changed: " + ", ".join(stale[case]["changed"]) for case in cases}
    costs = last_costs(data)
    fallback = sum(costs.values()) / max(1, len(costs))
    estimate = sum(costs.get(case, fallback) for case in cases) * options.runs * (1 + JUDGE_SHARE)
    return Plan(cases, rerecord, reasons, round(estimate, 2))


def print_plan(chosen: Plan, options) -> None:
    if options.github:
        print_warnings(chosen)
        return
    if chosen.rerecord:
        print("Re-record the mocks: build the jar, boot an empty Jeffrey, record into "
              f"{heap_work_dir()} (the path the heap cases name).")
    if not chosen.cases:
        print("Nothing to run: every mocked case's replay was recorded on the inputs the plugin holds now.")
        return
    print(f"Run {len(chosen.cases)} case(s), {options.runs} run(s) each, about ${chosen.estimate_usd} "
          f"(held to ${options.max_cost_usd}):")
    for case in chosen.cases:
        print(f"  {case:32s} {chosen.reasons[case]}")


def print_warnings(chosen: Plan) -> None:
    """The plan as GitHub Actions warnings: a pull request shows what the Agent Plugin page no longer describes."""
    page = usage_map.DEFAULT_OUT.relative_to(ROOT)
    if chosen.rerecord:
        print(f"::warning file={MOCKS.relative_to(ROOT)}::The server or the recorder changed since the eval mocks "
              f"were recorded: re-record them and run every case (/refresh-agent-plugin-page).")
    for case in chosen.cases if not chosen.rerecord else []:
        print(f"::warning file={page}::Agent Plugin page: case {case} is stale ({chosen.reasons[case]})")
    if chosen.cases:
        print(f"{len(chosen.cases)} case(s) to run again, about ${chosen.estimate_usd}: /refresh-agent-plugin-page")
    else:
        print("The Agent Plugin page describes the plugin and the server as they are.")


def heap_work_dir() -> Path:
    return Path(json.loads(RECORDED_PROFILES.read_text())["heapDumpPath"]).parent


def java_home() -> str:
    home = os.environ.get("JAVA_HOME")
    if not home:
        raise SystemExit("JAVA_HOME is not set: the build and the recording need a JDK 25")
    return home


def free_port() -> int:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def wait_for(url: str) -> None:
    deadline = time.monotonic() + STARTUP_SECONDS
    while time.monotonic() < deadline:
        try:
            urllib.request.urlopen(urllib.request.Request(url, data=b"{}", method="POST"), timeout=5)
            return
        except urllib.error.HTTPError:
            return
        except OSError:
            time.sleep(3)
    raise SystemExit(f"Jeffrey did not answer at {url} within {STARTUP_SECONDS} s")


def rerecord(options) -> None:
    env = {**os.environ, "JAVA_HOME": java_home()}
    print("Building the Microscope jar…", flush=True)
    subprocess.run([options.mvn, "-q", "-pl", "build/build-microscope", "-am", "package", "-DskipTests"],
                   cwd=ROOT, env=env, check=True)
    home = Path(tempfile.mkdtemp(prefix="jeffrey-record-home-"))
    port = free_port()
    url = f"http://localhost:{port}/api/mcp"
    log = (home / "microscope.log").open("w")
    server = subprocess.Popen([str(Path(env["JAVA_HOME"]) / "bin/java"), "--add-modules", "jdk.incubator.vector",
                               f"-Djeffrey.microscope.home.dir={home}", f"-Dserver.port={port}", "-jar", str(JAR)],
                              stdout=log, stderr=subprocess.STDOUT)
    try:
        wait_for(url)
        snapshot = [sys.executable, str(SCRIPTS / "mcp-snapshot.py"), "--url", url]
        if subprocess.run(snapshot + ["--check"], cwd=ROOT).returncode != 0:
            print("tools/list drifted: the snapshot is regenerated from this jar.", flush=True)
            regenerated = subprocess.run(snapshot, cwd=ROOT, capture_output=True, text=True, check=True).stdout
            (ROOT / "build/mcp/tools-list.snapshot.json").write_text(regenerated)
        print(f"Recording the mocks into {heap_work_dir()}…", flush=True)
        subprocess.run([sys.executable, str(SCRIPTS / "record-plugin-eval-mocks.py"), "--url", url,
                        "--work-dir", str(heap_work_dir()), "--reuse"], cwd=ROOT, check=True)
    finally:
        server.terminate()
        server.wait(timeout=60)
        log.close()
        shutil.rmtree(home, ignore_errors=True)


def run_cases(cases: list[str], options, out_dir: Path) -> list[Path]:
    """One `claude plugin eval` per case, each held to what is left of the budget; the result files it wrote."""
    results, spent = [], 0.0
    for case in cases:
        left = options.max_cost_usd - spent
        if left <= 0:
            print(f"Budget spent: {case} and the rest are not run.", file=sys.stderr)
            break
        result = out_dir / f"{case}.json"
        command = [options.claude, "plugin", "eval", str(PLUGIN), "--case", case, "--scaffold", "--ablation", "none",
                   "--threshold", "1", "--keep-temp", "--runs", str(options.runs), "-j", str(options.concurrency),
                   "--max-cost-usd", f"{left:.2f}", "--no-publish", "--trust-plugin", "--json", str(result)]
        print(f"Running {case}…", flush=True)
        with (out_dir / f"{case}.log").open("w") as log:
            subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        if result.is_file():
            results.append(result)
            spent += cost_of(json.loads(result.read_text()))
    print(f"Spent ${spent:.2f}.")
    return results


def cost_of(result: dict) -> float:
    return sum((run.get("costUsd") or 0) + (run.get("judgeCostUsd") or 0)
               for case in result["cases"] for run in case["arms"]["with"])


def plugin_digest() -> str:
    """Everything a replay's inputs are read from, as one digest: unchanged across the runs, or the stamps lie."""
    parts = usage_map.tree(PLUGIN / "skills", PLUGIN) + usage_map.tree(PLUGIN / "agents", PLUGIN)
    parts += usage_map.tree(PLUGIN / "evals", PLUGIN)
    return usage_map.digest([part for part in parts if "/results/" not in part[0]])


def report(results: list[Path]) -> None:
    print("\n| Case | Passed | Score | Failed graders |\n|---|---|---|---|")
    for path in results:
        for case in json.loads(path.read_text())["cases"]:
            runs = case["arms"]["with"]
            failed = [f"run {index}: {grader['name']} — {str(grader.get('explanation') or '')[:100]}"
                      for index, run in enumerate(runs, 1) for grader in run.get("graders", []) if not grader.get("passed")]
            print(f"| {case['name']} | {sum(1 for run in runs if run.get('passed'))} of {len(runs)} "
                  f"| {case['aggregates']['score']:.2f} | {'<br>'.join(failed) or '—'} |")


def remove_kept_runs(results: list[Path]) -> None:
    """The harness keeps each run's directory for the map to read; once the map holds the replays they can go. They
    are written by the plugin under test, so they are made writable and removed, never run in."""
    for path in results:
        for case in json.loads(path.read_text())["cases"]:
            for run in case["arms"]["with"]:
                trace = Path(run.get("tracePath") or "")
                kept = next((parent for parent in trace.parents if parent.name.startswith(EVAL_TEMP_PREFIX)), None)
                if kept is not None and kept.is_dir():
                    for each in [kept, *kept.rglob("*")]:
                        if not each.is_symlink():
                            each.chmod(each.stat().st_mode | stat.S_IWUSR | stat.S_IRUSR | stat.S_IXUSR)
                    shutil.rmtree(kept, ignore_errors=True)


def check() -> bool:
    ok = True
    for script in ("test-plugin-usage-map.py", "test-plugin-eval-mocks.py", "check-plugin-references.py"):
        if subprocess.run([sys.executable, str(SCRIPTS / script)], cwd=ROOT, capture_output=True).returncode != 0:
            print(f"{script} failed: run it to see why.", file=sys.stderr)
            ok = False
    return ok


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--run", action="store_true", help="carry the plan out; without it, only print it")
    parser.add_argument("--cases", nargs="+", help="run these mocked cases instead of the stale ones")
    parser.add_argument("--all", action="store_true", help="run every mocked case")
    parser.add_argument("--rerecord", choices=("auto", "yes", "no"), default="auto",
                        help="re-record the mocks: auto when the server or the recorder changed since the last recording")
    parser.add_argument("--runs", type=int, default=DEFAULT_RUNS)
    parser.add_argument("--max-cost-usd", type=float, default=DEFAULT_MAX_COST_USD)
    parser.add_argument("--concurrency", type=int, default=3)
    parser.add_argument("--keep-runs", action="store_true", help="keep the harness's run directories afterwards")
    parser.add_argument("--github", action="store_true", help="print the plan as GitHub Actions warnings (with no --run)")
    parser.add_argument("--claude", default="claude")
    parser.add_argument("--mvn", default="mvn")
    options = parser.parse_args(argv)

    out = usage_map.DEFAULT_OUT
    data = json.loads(out.read_text()) if out.is_file() else {}
    chosen = plan(options, data)
    print_plan(chosen, options)
    if not options.run or (not chosen.cases and not chosen.rerecord):
        if not options.run and chosen.cases:
            print("\nNothing has been spent. Add --run to carry this out.")
        return 0

    subprocess.run([sys.executable, str(SCRIPTS / "plugin-usage-map.py"), "--wiring-only"], cwd=ROOT, check=True)
    if chosen.rerecord:
        rerecord(options)
    before = plugin_digest()
    with tempfile.TemporaryDirectory(prefix="refresh-plugin-evals-") as scratch:
        results = run_cases(chosen.cases, options, Path(scratch))
        if plugin_digest() != before:
            print("The plugin or its evals changed while the cases ran: the map is not regenerated, since its stamps "
                  "would describe files the runs never saw. Run the changed cases again.", file=sys.stderr)
            return 1
        version = json.loads(IDENTITY.read_text())["version"]
        subprocess.run([sys.executable, str(SCRIPTS / "plugin-usage-map.py"), "--recorded-version", version],
                       cwd=ROOT, check=True)
        report(results)
        if not options.keep_runs:
            remove_kept_runs(results)
    stale = [entry["case"] for entry in usage_map.stale_replays(PLUGIN, json.loads(out.read_text()))
             if entry["suite"] == MOCKED]
    if stale:
        print(f"Still stale: {', '.join(stale)}")
    return 0 if check() else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
