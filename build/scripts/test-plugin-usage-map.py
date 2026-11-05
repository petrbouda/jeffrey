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
    assistant("2026-11-04T10:00:03.000Z", ("u4", "Agent", {"subagent_type": "microscope:heap-triage", "description": "Triage"})),
    assistant("2026-11-04T10:00:04.000Z", ("u5", J + "heap_getHeapSummary", {"profileId": "p"}), parent="u4"),
    assistant("2026-11-04T10:00:09.000Z", ("u6", "Grep", {"pattern": "AuditLog"})),
    "not json",
]


class StepsOfATrace(unittest.TestCase):

    def setUp(self):
        self.steps, self.lanes = usage_map.steps_of(TRACE)

    def test_keeps_the_calls_in_order_without_the_tool_search(self):
        self.assertEqual([(s["kind"], s["name"]) for s in self.steps], [
            ("skill", "analyze-heap"), ("jeffrey", "recordings_analyzeFile"), ("agent", "heap-triage"),
            ("jeffrey", "heap_getHeapSummary"), ("other", "Grep")])
        self.assertEqual([s["t"] for s in self.steps], [0, 2500, 3000, 4000, 9000])

    def test_puts_an_agents_calls_in_its_own_lane(self):
        self.assertEqual(self.lanes, {"main": "main", "u4": "heap-triage"})
        self.assertEqual([s["lane"] for s in self.steps], ["main", "main", "main", "u4", "main"])

    def test_charges_each_call_to_the_active_skill_or_to_its_agent(self):
        by = [s.get("by") for s in self.steps]
        self.assertEqual(by, [None, ["skill", "analyze-heap"], ["skill", "analyze-heap"], ["agent", "heap-triage"], None])

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
                                            "Call `heap_prepare`, then hand over to `microscope:heap-triage` "
                                            "or `report`.\n```\nheap_oql\n```\n")
            (skill / "references/more.md").write_text("Also `mcp__jeffrey__heap_status`.\n")
            (Path(tmp) / "skills/report").mkdir()
            (Path(tmp) / "skills/report/SKILL.md").write_text("---\nname: report\n---\nBack to `analyze-heap`.\n")
            skills = usage_map.skills_of(Path(tmp), {"heap_prepare", "heap_oql", "heap_status"},
                                         ["analyze-heap", "report"], ["heap-triage"])
        self.assertEqual(skills[0]["tools"], {"heap_prepare": 1, "heap_status": 1})
        self.assertEqual(skills[0]["agents"], {"heap-triage": 1})
        self.assertEqual(skills[0]["skills"], {"report": 1})
        self.assertEqual(skills[0]["description"], "Heap dumps")

    def test_expands_an_agents_wildcards_and_withholds_its_denied_tools(self):
        front = ("tools: mcp__plugin_microscope_jeffrey__heap_*\n"
                 "disallowedTools:\n  - mcp__plugin_microscope_jeffrey__heap_prepare\n")
        names = {"heap_prepare", "heap_oql", "jvm_gc"}
        self.assertEqual(usage_map.agent_tools(front, "tools", names), ["heap_oql", "heap_prepare"])
        self.assertEqual(usage_map.agent_tools(front, "disallowedTools", names), ["heap_prepare"])


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
        self.assertLessEqual(set(self.data["usage"]["agent"]), agents)

    def test_holds_the_wiring_the_plugin_and_the_snapshot_describe_now(self):
        snapshot = json.loads((ROOT / usage_map.references.TOOLS_SNAPSHOT).read_text())
        fresh = usage_map.wiring_of(ROOT / PLUGIN_DIR, snapshot)
        for key, entries in fresh.items():
            committed = {entry["name"]: entry for entry in self.data[key]}
            stale = sorted(entry["name"] for entry in entries if committed.get(entry["name"]) != entry)
            self.assertEqual(stale, [], f"the committed map's {key} are stale: "
                                        "run `python3 build/scripts/plugin-usage-map.py --wiring-only`")


class WiringOnly(unittest.TestCase):
    """`--wiring-only` refreshes the documented wiring of a map and keeps its runs, without any trace."""

    def test_rewrites_the_wiring_and_keeps_the_runs(self):
        committed = json.loads(COMMITTED.read_text())
        stale = json.loads(COMMITTED.read_text())
        stale["skills"][0]["tools"] = {}
        stale["agents"] = []
        stale["plugin"] = "0.0.1"
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "plugin-usage.json"
            out.write_text(usage_map.to_json(stale))
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(usage_map.main(["--wiring-only", "--out", str(out)]), 0)
            refreshed = json.loads(out.read_text())
        snapshot = json.loads((ROOT / usage_map.references.TOOLS_SNAPSHOT).read_text())
        for key, value in usage_map.wiring_of(ROOT / PLUGIN_DIR, snapshot).items():
            self.assertEqual(refreshed[key], value)
        for key in ("usage", "observed", "replays"):
            self.assertEqual(refreshed[key], committed[key])
        self.assertEqual(refreshed["plugin"], "0.0.1")

    def test_needs_a_map_to_refresh(self):
        with tempfile.TemporaryDirectory() as tmp:
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(usage_map.main(["--wiring-only", "--out", str(Path(tmp) / "none.json")]), 1)


if __name__ == "__main__":
    unittest.main()
