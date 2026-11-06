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
"""Offline tests for refresh-plugin-evals.py: what it plans, never a run; it calls neither a model nor Jeffrey."""
import argparse
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("refresh", ROOT / "build/scripts/refresh-plugin-evals.py")
refresh = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = refresh
spec.loader.exec_module(refresh)

CASES = ["fix-it", "gc-pauses", "slow-endpoints"]
DATA = {"replays": [{"suite": "mocked", "case": case, "runs": [{"costUsd": cost}, {"costUsd": cost}]}
                    for case, cost in (("fix-it", 0.3), ("gc-pauses", 0.1), ("slow-endpoints", 0.5))]}


def options(**given):
    defaults = {"rerecord": "auto", "all": False, "cases": None, "runs": 3, "max_cost_usd": 25.0}
    return argparse.Namespace(**(defaults | given))


class Planning(unittest.TestCase):

    def plan(self, stale=(), changed=(), **given):
        with patch.object(refresh, "answers_changed", return_value=list(changed)), \
                patch.object(refresh, "mocked_cases", return_value=CASES), \
                patch.object(refresh.usage_map, "stale_replays",
                             return_value=[{"suite": "mocked", "case": case, "changed": ["skills"]} for case in stale]):
            return refresh.plan(options(**given), DATA)

    def test_runs_the_stale_cases_and_says_what_moved(self):
        chosen = self.plan(stale=["gc-pauses"])
        self.assertEqual(["gc-pauses"], chosen.cases)
        self.assertFalse(chosen.rerecord)
        self.assertEqual("changed: skills", chosen.reasons["gc-pauses"])

    def test_estimates_from_each_cases_last_runs_with_the_judge(self):
        chosen = self.plan(stale=["fix-it", "slow-endpoints"])
        self.assertAlmostEqual(round((0.3 + 0.5) * 3 * 1.15, 2), chosen.estimate_usd)

    def test_a_changed_server_re_records_and_runs_every_case(self):
        # A re-recording gives every profile a new id, so every replay would describe other mocks.
        chosen = self.plan(stale=["gc-pauses"], changed=["jeffrey-microscope/x/src/main/java/A.java"])
        self.assertTrue(chosen.rerecord)
        self.assertEqual(CASES, chosen.cases)

    def test_named_cases_run_as_named_and_an_unknown_one_is_refused(self):
        self.assertEqual(["fix-it"], self.plan(stale=["gc-pauses"], cases=["fix-it"]).cases)
        with self.assertRaises(SystemExit):
            self.plan(cases=["no-such-case"])

    def test_no_re_recording_when_told_not_to(self):
        chosen = self.plan(stale=["gc-pauses"], changed=["jeffrey-microscope/x/src/main/java/A.java"], rerecord="no")
        self.assertFalse(chosen.rerecord)
        self.assertEqual(["gc-pauses"], chosen.cases)


class WhatChangesTheAnswers(unittest.TestCase):

    def test_only_server_main_sources_and_the_recorder_count(self):
        def git(*arguments):
            if arguments[0] == "log":
                return "abc\n"
            if arguments[0] == "diff":
                return "\n".join(["jeffrey-microscope/core/src/main/java/A.java",
                                  "jeffrey-microscope/core/src/test/java/ATest.java",
                                  "jeffrey-microscope/pages-microscope/src/router/x.ts",
                                  "build/scripts/record-plugin-eval-mocks.py"])
            return ""
        with patch.object(refresh, "git", side_effect=git):
            self.assertEqual(["build/scripts/record-plugin-eval-mocks.py",
                              "jeffrey-microscope/core/src/main/java/A.java"], refresh.answers_changed())


class Cleanup(unittest.TestCase):

    def test_removes_only_the_harness_run_directories(self):
        with tempfile.TemporaryDirectory() as tmp:
            kept = Path(tmp) / "claude-eval-abc123"
            (kept / "out").mkdir(parents=True)
            (kept / "out/trace.jsonl").write_text("{}\n")
            other = Path(tmp) / "not-a-run"
            (other / "out").mkdir(parents=True)
            (other / "out/trace.jsonl").write_text("{}\n")
            result = Path(tmp) / "result.json"
            result.write_text(json.dumps({"cases": [{"arms": {"with": [
                {"tracePath": str(kept / "out/trace.jsonl")}, {"tracePath": str(other / "out/trace.jsonl")}]}}]}))
            refresh.remove_kept_runs([result])
            self.assertFalse(kept.exists())
            self.assertTrue(other.exists())


if __name__ == "__main__":
    unittest.main()
