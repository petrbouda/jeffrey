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
"""Offline tests for plugin-usage-map.py: trace reading, wiring and the committed map; no eval traces needed."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

from plugin_layout import PLUGIN_DIR

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("usage_map", ROOT / "build/scripts/plugin-usage-map.py")
usage_map = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = usage_map
spec.loader.exec_module(usage_map)
COMMITTED = usage_map.DEFAULT_OUT

J = usage_map.JEFFREY_PREFIX


def assistant(at: str, *calls, parent=None) -> str:
    content = [{"type": "tool_use", "id": call_id, "name": name, "input": arguments} for call_id, name, arguments in calls]
    return json.dumps({"type": "assistant", "timestamp": at, "parent_tool_use_id": parent, "message": {"content": content}})


TRACE = [
    json.dumps({"type": "system", "subtype": "init"}),
    assistant("2026-11-04T10:00:00.000Z", ("u1", "Skill", {"skill": "microscope:analyze-heap", "args": "/tmp/a.hprof"})),
    assistant("2026-11-04T10:00:01.000Z", ("u2", "ToolSearch", {"query": "select:x"})),
    assistant("2026-11-04T10:00:02.500Z", ("u3", J + "recordings_analyzeFile", {"path": "/tmp/a.hprof"})),
    assistant("2026-11-04T10:00:03.000Z", ("u4", "Agent", {"subagent_type": "microscope:heap-analyst", "description": "Triage"})),
    assistant("2026-11-04T10:00:04.000Z", ("u5", J + "heap_getHeapSummary", {"profileId": "p"}), parent="u4"),
    assistant("2026-11-04T10:00:09.000Z", ("u6", "Grep", {"pattern": "AuditLog"})),
    "not json",
]


class StepsOfATrace(unittest.TestCase):

    def setUp(self):
        self.steps, self.lanes = usage_map.steps_of(TRACE)

    def test_keeps_the_calls_in_order_without_the_tool_search(self):
        self.assertEqual([(s["kind"], s["name"]) for s in self.steps], [
            ("skill", "analyze-heap"), ("jeffrey", "recordings_analyzeFile"), ("agent", "heap-analyst"),
            ("jeffrey", "heap_getHeapSummary"), ("other", "Grep")])
        self.assertEqual([s["t"] for s in self.steps], [0, 2500, 3000, 4000, 9000])

    def test_puts_an_agents_calls_in_its_own_lane(self):
        self.assertEqual(self.lanes, {"main": "main", "u4": "heap-analyst"})
        self.assertEqual([s["lane"] for s in self.steps], ["main", "main", "main", "u4", "main"])

    def test_charges_each_call_to_the_active_skill_or_to_its_agent(self):
        by = [s.get("by") for s in self.steps]
        self.assertEqual(by, [None, ["skill", "analyze-heap"], ["skill", "analyze-heap"], ["agent", "heap-analyst"], None])

    def test_a_hand_off_to_the_clients_own_agent_is_no_use_of_the_plugins(self):
        steps, lanes = usage_map.steps_of([
            assistant("2026-11-04T10:00:00Z", ("u1", "Skill", {"skill": "microscope:code-insight"})),
            assistant("2026-11-04T10:00:01Z", ("u2", "Agent", {"subagent_type": "Explore", "description": "Find it"})),
            assistant("2026-11-04T10:00:02Z", ("u3", "Grep", {"pattern": "count"}), parent="u2"),
            assistant("2026-11-04T10:00:03Z", ("u4", "Agent", {"description": "No type given"}))])
        self.assertEqual([(s["kind"], s["name"]) for s in steps], [
            ("skill", "code-insight"), ("other", "Agent(Explore)"), ("other", "Grep"),
            ("other", "Agent(general-purpose)")])
        self.assertEqual(lanes, {"main": "main", "u2": "Explore", "u4": "general-purpose"})
        self.assertNotIn("by", steps[1])

    def test_cuts_long_inputs(self):
        steps, _ = usage_map.steps_of([assistant("2026-11-04T10:00:00Z", ("u1", "Bash", {"command": "x" * 500}))])
        self.assertEqual(len(steps[0]["input"]), usage_map.INPUT_CHARS)
        self.assertTrue(steps[0]["input"].endswith("…"))


class Wiring(unittest.TestCase):

    def test_reads_a_skills_tools_hand_overs_and_references(self):
        with tempfile.TemporaryDirectory() as tmp:
            skill = Path(tmp) / "skills/analyze-heap"
            (skill / "references").mkdir(parents=True)
            (skill / "SKILL.md").write_text("---\nname: analyze-heap\ndescription: Heap dumps\n---\n"
                                            "Call `heap_prepare`, then hand over to `microscope:heap-analyst` "
                                            "or `report`.\n```\nheap_oql\n```\n")
            (skill / "references/more.md").write_text("Also `mcp__jeffrey__heap_status`.\n")
            (Path(tmp) / "skills/report").mkdir()
            (Path(tmp) / "skills/report/SKILL.md").write_text("---\nname: report\n---\nBack to `analyze-heap`.\n")
            skills = usage_map.skills_of(Path(tmp), {"heap_prepare", "heap_oql", "heap_status"},
                                         ["analyze-heap", "report"], ["heap-analyst"])
        self.assertEqual(skills[0]["tools"], {"heap_prepare": 1, "heap_status": 1})
        self.assertEqual(skills[0]["agents"], {"heap-analyst": 1})
        self.assertEqual(skills[0]["skills"], {"report": 1})
        self.assertEqual(skills[0]["description"], "Heap dumps")

    def test_expands_an_agents_wildcards_and_withholds_its_denied_tools(self):
        front = ("tools: mcp__plugin_microscope_jeffrey__heap_*\n"
                 "disallowedTools:\n  - mcp__plugin_microscope_jeffrey__heap_prepare\n")
        names = {"heap_prepare", "heap_oql", "jvm_gc"}
        self.assertEqual(usage_map.agent_tools(front, "tools", names), ["heap_oql", "heap_prepare"])
        self.assertEqual(usage_map.agent_tools(front, "disallowedTools", names), ["heap_prepare"])


class HistoricalRuns(unittest.TestCase):

    def test_keeps_recorded_agent_names_in_totals_edges_and_replays(self):
        trace = [
            assistant("2026-11-04T10:00:00Z", ("a1", "Agent", {"subagent_type": "microscope:profile-analyst"})),
            assistant("2026-11-04T10:00:01Z", ("t1", J + "jvm_gc", {}), parent="a1"),
            assistant("2026-11-04T10:00:02Z", ("a2", "Agent", {"subagent_type": "microscope:heap-triage"}), parent="a1"),
            assistant("2026-11-04T10:00:03Z", ("t2", J + "heap_getHeapSummary", {}), parent="a2"),
        ]
        with tempfile.TemporaryDirectory() as tmp:
            plugin = Path(tmp)
            trace_path = plugin / "trace.jsonl"
            trace_path.write_text("\n".join(trace))
            case = plugin / "evals/cases/delegation"
            case.mkdir(parents=True)
            (case / "prompt.md").write_text("---\nname: delegation\ntags: [routing]\n---\n\nDelegate it.\n")
            result = plugin / "evals/results/old/aggregate-result.json"
            result.parent.mkdir(parents=True)
            result.write_text(json.dumps({"cases": [{"name": "delegation", "arms": {"with": [
                {"tracePath": str(trace_path)}]}}]}))
            data = usage_map.usage_of(plugin)
            out = plugin / "map.json"
            out.write_text(usage_map.to_json({"plugin": "1.0.30"} | data))
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(usage_map.main(["--wiring-only", "--out", str(out)]), 0)
            refreshed = json.loads(out.read_text())
        self.assertEqual(data["usage"]["agent"], {"profile-analyst": 1, "heap-triage": 1})
        self.assertEqual(data["observed"], {
            "a:profile-analyst": {"t:jvm_gc": 1, "a:heap-triage": 1},
            "a:heap-triage": {"t:heap_getHeapSummary": 1}})
        replay = data["replays"][0]["runs"][0]
        self.assertEqual(replay["lanes"], {"main": "main", "a1": "profile-analyst", "a2": "heap-triage"})
        self.assertEqual([s["name"] for s in replay["steps"]],
                         ["profile-analyst", "jvm_gc", "heap-triage", "heap_getHeapSummary"])
        self.assertEqual([s.get("by") for s in replay["steps"]],
                         [None, ["agent", "profile-analyst"], ["agent", "profile-analyst"], ["agent", "heap-triage"]])
        for key, value in data.items():
            self.assertEqual(refreshed[key], value)
        self.assertEqual(refreshed.get("agentAliases"), {"profile-analyst": "jfr-analyst", "heap-triage": "heap-analyst"})


class RecordedVersion(unittest.TestCase):

    def run_main(self, args):
        stderr = io.StringIO()
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(stderr):
            try:
                code = usage_map.main(args)
            except SystemExit as error:
                code = error.code
        return code, stderr.getvalue()

    def test_requires_a_recorded_version_before_touching_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "map.json"
            out.write_text("existing output")
            code, error = self.run_main(["--out", str(out)])
            self.assertNotEqual(code, 0)
            self.assertIn("--recorded-version is required", error)
            self.assertEqual(out.read_text(), "existing output")

    def test_wiring_refresh_rejects_a_recorded_version_without_touching_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "map.json"
            out.write_text(usage_map.to_json({"plugin": "1.0.30"}))
            before = out.read_bytes()
            code, error = self.run_main(["--wiring-only", "--recorded-version", "1.0.31", "--out", str(out)])
            self.assertNotEqual(code, 0)
            self.assertIn("--recorded-version cannot be combined with --wiring-only", error)
            self.assertEqual(out.read_bytes(), before)

    def test_full_generation_uses_declared_version_and_current_wiring_version(self):
        with tempfile.TemporaryDirectory() as tmp:
            plugin = Path(tmp)
            (plugin / "plugin.json").write_text(json.dumps({"version": "1.0.31"}))
            trace = plugin / "trace.jsonl"
            trace.write_text(assistant("2026-11-04T10:00:00Z", ("a1", "Agent", {
                "subagent_type": "microscope:profile-analyst"})))
            case = plugin / "evals/cases/delegation"
            case.mkdir(parents=True)
            (case / "prompt.md").write_text("---\nname: delegation\ntags: [routing]\n---\n\nDelegate it.\n")
            result = plugin / "evals/results/old/aggregate-result.json"
            result.parent.mkdir(parents=True)
            result.write_text(json.dumps({"cases": [{"name": "delegation", "arms": {"with": [
                {"tracePath": str(trace)}]}}]}))
            out = plugin / "map.json"
            with patch.object(usage_map, "PLUGIN", plugin):
                code, error = self.run_main(["--recorded-version", "1.0.30", "--out", str(out)])
            self.assertEqual(code, 0, error)
            generated = json.loads(out.read_text())
        self.assertEqual(generated["plugin"], "1.0.30")
        self.assertEqual(generated["wiringPlugin"], "1.0.31")
        self.assertEqual(generated["usage"]["agent"], {"profile-analyst": 1})


class LatestRuns(unittest.TestCase):
    """A case run again counts once, by its latest runs; a case no longer in its suite, and a run the harness
    refused, are left out."""

    def test_counts_each_current_case_by_its_latest_runs(self):
        with tempfile.TemporaryDirectory() as tmp:
            plugin = Path(tmp)
            case = plugin / "evals/cases/kept"
            case.mkdir(parents=True)
            (case / "prompt.md").write_text("---\nname: kept\ntags: [routing]\n---\n\nKeep it.\n")
            trace = plugin / "trace.jsonl"
            trace.write_text(assistant("2026-11-05T10:00:00Z", ("k1", "Skill", {"skill": "microscope:help"})) + "\n")
            refused = plugin / "evals/results/c/aggregate-result.json"
            refused.parent.mkdir(parents=True)
            refused.write_text(json.dumps({"startedAt": "c", "cases": [
                {"name": "kept", "arms": {"with": [{"tracePath": str(trace), "error": "exit 1: refused"}]}}]}))
            moved = plugin / "evals-live/results/a/aggregate-result.json"
            moved.parent.mkdir(parents=True)
            moved.write_text(json.dumps({"startedAt": "a", "cases": [
                {"name": "kept", "arms": {"with": [{"tracePath": str(trace)}]}}]}))
            for stamp, cases in (("a", ["kept", "gone"]), ("b", ["kept"])):
                result = plugin / f"evals/results/{stamp}/aggregate-result.json"
                result.parent.mkdir(parents=True)
                result.write_text(json.dumps({"startedAt": stamp, "cases": [
                    {"name": name, "arms": {"with": [{"tracePath": str(trace)}, {"tracePath": str(trace)}]}}
                    for name in cases]}))
            usage = usage_map.usage_of(plugin)
            self.assertEqual([("kept", "b")], [(replay["case"], replay["at"]) for replay in usage["replays"]])
            self.assertEqual(2, usage["usage"]["runs"])
            self.assertEqual({"help": 2}, usage["usage"]["skill"])

    def test_keeps_each_runs_reply_and_every_graders_reason(self):
        # A failed run is read after its trace is gone: the reply a last-message judge read, and why each grader
        # decided as it did; a grader that gave no reason keeps none.
        def said(text, parent=None):
            return json.dumps({"type": "assistant", "parent_tool_use_id": parent,
                               "message": {"content": [{"type": "text", "text": text}]}})
        with tempfile.TemporaryDirectory() as tmp:
            plugin = Path(tmp)
            case = plugin / "evals/cases/judged"
            case.mkdir(parents=True)
            (case / "prompt.md").write_text("---\nname: judged\ntags: [routing]\n---\n\nJudge it.\n")
            trace = plugin / "trace.jsonl"
            trace.write_text("\n".join([said("Looking."), assistant("2026-11-05T10:00:00Z", ("k1", "Skill", {"skill": "help"})),
                                        said("The analyst's own words.", parent="k1"),
                                        said("x" * (usage_map.REPLY_CHARS + 50))]) + "\n")
            result = plugin / "evals/results/a/aggregate-result.json"
            result.parent.mkdir(parents=True)
            result.write_text(json.dumps({"startedAt": "a", "cases": [{"name": "judged", "arms": {"with": [
                {"tracePath": str(trace), "passed": False, "graders": [
                    {"name": "offers-implement-fixes", "passed": False, "explanation": "  No offer by that name.  "},
                    {"name": "no-edit", "passed": True, "explanation": ""}]}]}}]}))
            run = usage_map.usage_of(plugin)["replays"][0]["runs"][0]
            self.assertEqual(usage_map.REPLY_CHARS, len(run["reply"]))
            self.assertTrue(run["reply"].startswith("xxx") and run["reply"].endswith("…"))
            self.assertEqual(usage_map.REPLY_CHARS + 50, run["replyChars"])
            self.assertEqual([{"name": "offers-implement-fixes", "passed": False, "explanation": "No offer by that name."},
                              {"name": "no-edit", "passed": True}], run["graders"])

    def _plugin_with_one_run(self, tmp):
        """A plugin with two skills, one agent, one case and a mock, and one run of the case that reached one skill
        and the agent."""
        plugin = Path(tmp)
        for name, description in (("first", "Reads profiles."), ("second", "Writes reports.")):
            (plugin / "skills" / name / "references").mkdir(parents=True)
            (plugin / "skills" / name / "SKILL.md").write_text(f"---\nname: {name}\ndescription: {description}\n---\n\nBody.\n")
            (plugin / "skills" / name / "references" / "more.md").write_text("More.\n")
        (plugin / "agents").mkdir()
        (plugin / "agents" / "jfr-analyst.md").write_text("---\nname: jfr-analyst\n---\n\nReads.\n")
        case = plugin / "evals/cases/kept"
        (case / "graders").mkdir(parents=True)
        (case / "prompt.md").write_text("---\nname: kept\ntags: [routing]\n---\n\nKeep it.\n")
        (case / "graders" / "judge.md").write_text("---\ntype: llm\n---\n\nJudge it.\n")
        (plugin / "evals/mocks/jeffrey").mkdir(parents=True)
        (plugin / "evals/mocks/jeffrey/profiles_list.md").write_text("---\ntype: fixed\n---\n\n{}\n")
        trace = plugin / "trace.jsonl"
        trace.write_text("\n".join([
            assistant("2026-11-05T10:00:00Z", ("k1", "Skill", {"skill": "microscope:first"})),
            assistant("2026-11-05T10:00:01Z", ("k2", "Agent", {"subagent_type": "microscope:profile-analyst",
                                                               "description": "Read"}))]) + "\n")
        result = plugin / "evals/results/a/aggregate-result.json"
        result.parent.mkdir(parents=True)
        result.write_text(json.dumps({"startedAt": "a", "cases": [
            {"name": "kept", "arms": {"with": [{"tracePath": str(trace), "passed": True}]}}]}))
        return plugin, usage_map.usage_of(plugin, "1.0.0")

    def test_stamps_each_replay_with_the_inputs_its_runs_depended_on(self):
        with tempfile.TemporaryDirectory() as tmp:
            plugin, usage = self._plugin_with_one_run(tmp)
            inputs = usage["replays"][0]["inputs"]
            self.assertEqual(set(usage_map.INPUT_GROUPS), set(inputs))
            self.assertEqual([], usage_map.stale_replays(plugin, usage))

    def test_a_change_under_a_replay_names_the_group_it_moved(self):
        # The skill its runs reached (a reference too), the agent under its historical name, every skill's
        # description, the case's graders, the recorded answers; a skill the runs never reached is not one of them.
        changes = {"skills": ("skills/first/references/more.md", "Changed.\n"),
                   "agents": ("agents/jfr-analyst.md", "---\nname: jfr-analyst\n---\n\nReads more.\n"),
                   "routing": ("skills/second/SKILL.md", "---\nname: second\ndescription: Writes reports, now.\n---\n\nBody.\n"),
                   "case": ("evals/cases/kept/graders/judge.md", "---\ntype: llm\n---\n\nJudge it harder.\n"),
                   "answers": ("evals/mocks/jeffrey/profiles_list.md", "---\ntype: fixed\n---\n\n{\"profiles\": []}\n")}
        for group, (path, text) in changes.items():
            with self.subTest(group=group), tempfile.TemporaryDirectory() as tmp:
                plugin, usage = self._plugin_with_one_run(tmp)
                (plugin / path).write_text(text)
                self.assertEqual([{"suite": "mocked", "case": "kept", "changed": [group]}],
                                 usage_map.stale_replays(plugin, usage))
        with tempfile.TemporaryDirectory() as tmp:
            plugin, usage = self._plugin_with_one_run(tmp)
            (plugin / "skills/second/references/more.md").write_text("Not reached.\n")
            self.assertEqual([], usage_map.stale_replays(plugin, usage))

    def test_a_replay_recorded_before_inputs_were_kept_or_missing_is_stale(self):
        with tempfile.TemporaryDirectory() as tmp:
            plugin, usage = self._plugin_with_one_run(tmp)
            without = {**usage, "replays": [{key: value for key, value in usage["replays"][0].items() if key != "inputs"}]}
            self.assertEqual([{"suite": "mocked", "case": "kept", "changed": None}],
                             usage_map.stale_replays(plugin, without))
            self.assertEqual([{"suite": "mocked", "case": "kept", "changed": ["no replay"]}],
                             usage_map.stale_replays(plugin, {**usage, "replays": []}))

    def test_the_stale_report_warns_and_fails_only_when_asked(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "map.json"
            out.write_text(json.dumps({"replays": []}))
            stale = [{"suite": "mocked", "case": "kept", "changed": ["skills"]}]
            with patch.object(usage_map, "stale_replays", return_value=stale), \
                    patch.object(usage_map, "ROOT", Path(tmp)), \
                    contextlib.redirect_stdout(io.StringIO()) as printed, contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(0, usage_map.report_stale(out, github=True, strict=False))
                self.assertEqual(1, usage_map.report_stale(out, github=False, strict=True))
            self.assertIn("::warning file=map.json::mocked case kept", printed.getvalue())
            self.assertIn("changed: skills", printed.getvalue())

    def test_a_run_that_wrote_no_reply_keeps_none(self):
        self.assertIsNone(usage_map.reply_of(TRACE))

    def test_keeps_an_earlier_replay_of_a_current_case_with_no_runs_on_disk(self):
        with tempfile.TemporaryDirectory() as tmp:
            plugin = Path(tmp)
            for name in ("kept", "rerun"):
                case = plugin / f"evals/cases/{name}"
                case.mkdir(parents=True)
                (case / "prompt.md").write_text(f"---\nname: {name}\ntags: [routing]\n---\n\nAsk.\n")
            trace = plugin / "trace.jsonl"
            trace.write_text(assistant("2026-11-05T10:00:00Z", ("k1", "Skill", {"skill": "microscope:help"})) + "\n")
            result = plugin / "evals/results/b/aggregate-result.json"
            result.parent.mkdir(parents=True)
            result.write_text(json.dumps({"startedAt": "b", "cases": [
                {"name": "rerun", "arms": {"with": [{"tracePath": str(trace)}]}}]}))
            step = {"kind": "skill", "name": "report", "lane": "main"}
            old = {"suite": "mocked", "prompt": "", "at": "a", "runs": [{"run": 1, "steps": [step], "lanes": {}}]}
            earlier = {"plugin": "1.0.1", "replays": [old | {"case": "kept"}, old | {"case": "rerun"}, old | {"case": "gone"}]}
            usage = usage_map.usage_of(plugin, "1.0.2", earlier)
            self.assertEqual([("kept", "1.0.1", "a"), ("rerun", "1.0.2", "b")],
                             [(r["case"], r["plugin"], r["at"]) for r in usage["replays"]])
            self.assertEqual(["1.0.1", "1.0.2"], usage["pluginVersions"])
            self.assertEqual({"help": 1, "report": 1}, usage["usage"]["skill"])


class CommittedMap(unittest.TestCase):
    """The committed map names what the plugin and the server have now; a rename fails here, traces or not."""

    @classmethod
    def setUpClass(cls):
        cls.data = json.loads(COMMITTED.read_text())

    def test_names_every_tool_of_the_snapshot(self):
        snapshot = json.loads((ROOT / usage_map.references.TOOLS_SNAPSHOT).read_text())
        self.assertEqual(sorted(t["name"] for t in self.data["tools"]), sorted(t["name"] for t in snapshot["tools"]))

    def test_names_every_skill_and_agent_of_the_plugin(self):
        plugin = ROOT / PLUGIN_DIR
        self.assertEqual(sorted(s["name"] for s in self.data["skills"]),
                         sorted(p.parent.name for p in (plugin / "skills").glob("*/SKILL.md")))
        self.assertEqual(sorted(a["name"] for a in self.data["agents"]),
                         sorted(p.stem for p in (plugin / "agents").glob("*.md")))

    def test_its_runs_only_call_tools_skills_and_agents_it_names(self):
        tools = {t["name"] for t in self.data["tools"]}
        skills = {s["name"] for s in self.data["skills"]}
        agents = {a["name"] for a in self.data["agents"]}
        self.assertLessEqual(set(self.data["usage"]["jeffrey"]), tools)
        self.assertLessEqual(set(self.data["usage"]["skill"]), skills)
        aliases = self.data.get("agentAliases", {})
        self.assertLessEqual({aliases.get(name, name) for name in self.data["usage"]["agent"]}, agents)

    def test_historical_agent_references_resolve_to_current_wiring(self):
        agents = {a["name"] for a in self.data["agents"]}
        aliases = self.data.get("agentAliases", {})
        self.assertLessEqual(set(aliases.values()), agents)
        recorded = set(self.data["usage"]["agent"])
        for source, targets in self.data["observed"].items():
            for node in [source, *targets]:
                if node.startswith("a:"):
                    self.assertIn(node[2:], recorded)
        for case in self.data["replays"]:
            for run in case["runs"]:
                # The client's own agents a run handed work to (Explore): their lanes, not the plugin's agents.
                client = {step["name"][len("Agent("):-1] for step in run["steps"]
                          if step["kind"] == "other" and step["name"].startswith("Agent(")}
                for name in run["lanes"].values():
                    if name != "main" and name not in client:
                        self.assertIn(name, recorded)
                        self.assertIn(aliases.get(name, name), agents)
                for step in run["steps"]:
                    if step["kind"] == "agent":
                        self.assertIn(step["name"], recorded)
                    if step.get("by") and step["by"][0] == "agent":
                        self.assertIn(step["by"][1], recorded)
                        self.assertEqual(step["by"][1], run["lanes"][step["lane"]])

    def test_holds_the_wiring_the_plugin_and_the_snapshot_describe_now(self):
        snapshot = json.loads((ROOT / usage_map.references.TOOLS_SNAPSHOT).read_text())
        fresh = usage_map.wiring_of(ROOT / PLUGIN_DIR, snapshot)
        for key, value in fresh.items():
            self.assertEqual(self.data.get(key), value, f"the committed map's {key} is stale: "
                                                       "run `python3 build/scripts/plugin-usage-map.py --wiring-only`")


class Areas(unittest.TestCase):
    """Each skill and tool family sits in exactly one area, and each area's agent is one of the plugin's."""

    @classmethod
    def setUpClass(cls):
        cls.data = json.loads(COMMITTED.read_text())

    def assert_partitions(self, key, names):
        placed = [name for area in usage_map.AREAS for name in area[key]]
        twice = sorted({name for name in placed if placed.count(name) > 1})
        self.assertEqual(twice, [], f"{key} in more than one area")
        self.assertEqual(sorted(set(placed) - names), [], f"{key} in an area but not in the plugin or snapshot")
        self.assertEqual(sorted(names - set(placed)), [], f"{key} in no area: add each to one in AREAS")

    def test_every_skill_is_in_one_area(self):
        self.assert_partitions("skills", {s["name"] for s in self.data["skills"]})

    def test_every_tool_family_is_in_one_area(self):
        self.assert_partitions("families", {t["family"] for t in self.data["tools"]})

    def test_each_area_is_worked_by_one_of_the_plugins_agents(self):
        agents = {a["name"] for a in self.data["agents"]}
        worked = [area["agent"] for area in usage_map.AREAS if area["agent"]]
        self.assertLessEqual(set(worked), agents)
        self.assertEqual(len(worked), len(set(worked)))


class UseCases(unittest.TestCase):
    """Every eval case sits in exactly one use case and has its question in a line; a use case names only real
    cases and holds at least one."""

    @classmethod
    def setUpClass(cls):
        cls.cases = set(usage_map.case_names(ROOT / PLUGIN_DIR))
        cls.placed = [case for use_case in usage_map.USE_CASES for case in use_case["cases"]]

    def test_every_case_is_in_one_use_case(self):
        self.assertEqual(sorted({case for case in self.placed if self.placed.count(case) > 1}), [],
                         "a case in more than one use case")
        self.assertEqual(sorted(self.cases - set(self.placed)), [], "a case in no use case: add it to USE_CASES")

    def test_use_cases_name_only_cases_the_suites_hold(self):
        self.assertEqual(sorted(set(self.placed) - self.cases), [])

    def test_every_case_has_its_question_in_a_line(self):
        self.assertEqual(sorted(self.cases - set(usage_map.CASE_QUESTIONS)), [], "add it to CASE_QUESTIONS")
        self.assertEqual(sorted(set(usage_map.CASE_QUESTIONS) - self.cases), [])

    def test_every_use_case_holds_a_case_and_a_unique_name(self):
        names = [use_case["name"] for use_case in usage_map.USE_CASES]
        self.assertEqual(len(names), len(set(names)))
        self.assertEqual([use_case["name"] for use_case in usage_map.USE_CASES if not use_case["cases"]], [])

    def test_each_use_case_quotes_one_of_its_own_cases(self):
        self.assertEqual([use_case["name"] for use_case in usage_map.USE_CASES
                          if use_case["example"] not in use_case["cases"]], [])


class Workloads(unittest.TestCase):
    """Every eval case says what it runs on: one workload tag, or a place in NO_RECORDING."""

    @classmethod
    def setUpClass(cls):
        cls.cases = usage_map.case_workloads(ROOT / PLUGIN_DIR)

    def test_every_case_declares_its_workload_or_reads_no_recording(self):
        undeclared = sorted(case for case, workload in self.cases.items()
                            if workload is None and case not in usage_map.NO_RECORDING)
        self.assertEqual(undeclared, [], "add a workload tag (" + ", ".join(w["name"] for w in usage_map.WORKLOADS)
                         + ") to each case's prompt.md, or name it in NO_RECORDING")

    def test_no_recording_names_only_cases_without_a_workload(self):
        self.assertEqual(sorted(name for name in usage_map.NO_RECORDING if self.cases.get(name) is not None
                                or name not in self.cases), [])

    def test_case_names_are_unique_across_the_suites(self):
        plugin = ROOT / PLUGIN_DIR
        names = [p.parent.name for suite in usage_map.SUITES for p in (plugin / suite / "cases").glob("*/prompt.md")]
        self.assertEqual(len(names), len(set(names)))


class WiringOnly(unittest.TestCase):
    """`--wiring-only` refreshes the documented wiring of a map and keeps its runs, without any trace."""

    def test_rewrites_the_wiring_and_keeps_the_runs(self):
        committed = json.loads(COMMITTED.read_text())
        stale = json.loads(COMMITTED.read_text())
        stale["skills"][0]["tools"] = {}
        stale["agents"] = []
        stale["plugin"] = "0.0.1"
        stale["wiringPlugin"] = "0.0.2"
        stale["agentAliases"] = {"wrong": "alias"}
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "plugin-usage.json"
            out.write_text(usage_map.to_json(stale))
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(usage_map.main(["--wiring-only", "--out", str(out)]), 0)
            refreshed = json.loads(out.read_text())
            first = out.read_bytes()
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(usage_map.main(["--wiring-only", "--out", str(out)]), 0)
            self.assertEqual(out.read_bytes(), first)
        snapshot = json.loads((ROOT / usage_map.references.TOOLS_SNAPSHOT).read_text())
        for key, value in usage_map.wiring_of(ROOT / PLUGIN_DIR, snapshot).items():
            self.assertEqual(refreshed[key], value)
        for key in ("usage", "observed", "replays"):
            self.assertEqual(refreshed[key], committed[key])
        self.assertEqual(refreshed["plugin"], "0.0.1")
        self.assertEqual(refreshed["wiringPlugin"], json.loads((ROOT / PLUGIN_DIR / "plugin.json").read_text())["version"])
        self.assertEqual(refreshed["agentAliases"], {"profile-analyst": "jfr-analyst", "heap-triage": "heap-analyst"})

    def test_needs_a_map_to_refresh(self):
        with tempfile.TemporaryDirectory() as tmp:
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(usage_map.main(["--wiring-only", "--out", str(Path(tmp) / "none.json")]), 1)


if __name__ == "__main__":
    unittest.main()
