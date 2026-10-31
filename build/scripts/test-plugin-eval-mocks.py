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
"""Offline regression tests for recorded eval contracts; never call a model or Jeffrey."""
import importlib.util
import json
from pathlib import Path
import re
import sys
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import yaml

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('recorder', ROOT / 'build/scripts/record-plugin-eval-mocks.py')
recorder = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = recorder
spec.loader.exec_module(recorder)


def answer(value):
    return {'content': [{'type': 'text', 'text': json.dumps(value)}], 'structuredContent': value}


class ReplayContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.mocks = self.root / 'mocks'
        self.cases = self.root / 'cases'
        (self.cases / 'example/graders').mkdir(parents=True)
        for name, value in [('MOCKS', self.mocks), ('FIXTURES', self.mocks / 'fixtures'), ('MOCKED_CASES', self.cases)]:
            p = patch.object(recorder, name, value)
            p.start()
            self.addCleanup(p.stop)

    def capture(self, tool, arguments):
        schema = {'type': 'object', 'required': ['profileId', 'eventType'], 'properties': {
            'profileId': {'type': 'string'}, 'eventType': {'type': 'string'},
            'useWeight': {'type': 'boolean'}, 'startEpochMs': {'type': 'integer'},
            'endEpochMs': {'type': 'integer'}, 'excludeIdle': {'type': 'boolean'},
            'search': {'type': 'string'}, 'thresholdPct': {'type': 'number'}}}
        tools = [{'name': tool, 'inputSchema': schema}]
        recorded = recorder.Recorded()
        class Server:
            def call(self, name, args):
                return answer({'weighted': args.get('useWeight', True)})
        with patch.object(recorder, 'seed_calls', return_value=[(tool, arguments)]):
            recorder.crawl(Server(), tools, recorder.Profiles('a', 'b', 'h'), recorded)
        recorder.write_mocks(tools, recorded)
        path = self.cases / 'example/graders/recorded-call-semantics.md'
        self.assertTrue(path.exists(), 'every case must reject unsupported semantic inputs')
        return yaml.safe_load(path.read_text().split('---')[1])['pattern']

    def violates(self, pattern, tool, arguments):
        call = {'tool': 'mcp__plugin_microscope_jeffrey__' + tool, 'input': arguments, 'output': 'fixture'}
        result = subprocess.run(['node', '-e',
            'const x=JSON.parse(require("fs").readFileSync(0,"utf8")); process.stdout.write(String(new RegExp(x.pattern,"m").test(JSON.stringify(x.call))));'],
            input=json.dumps({'pattern': pattern, 'call': call}), text=True, capture_output=True, check=True)
        return result.stdout == 'true'

    def test_weighted_default_accepts_omission_and_true_but_rejects_false(self):
        args = {'profileId': 'b', 'eventType': 'jdk.ObjectAllocationSample'}
        pattern = self.capture('flamegraph_export', args)
        self.assertFalse(self.violates(pattern, 'flamegraph_export', args))
        self.assertFalse(self.violates(pattern, 'flamegraph_export', {**args, 'useWeight': True}))
        self.assertTrue(self.violates(pattern, 'flamegraph_export', {**args, 'useWeight': False}))

    def test_unrecorded_window_and_filter_are_rejected(self):
        args = {'profileId': 'b', 'eventType': 'jdk.ObjectAllocationSample'}
        pattern = self.capture('flamegraph_export', args)
        for change in [{'startEpochMs': 10}, {'endEpochMs': 20}, {'excludeIdle': True}, {'search': 'formatLine'}]:
            with self.subTest(change=change):
                self.assertTrue(self.violates(pattern, 'flamegraph_export', {**args, **change}))

    def test_captured_window_only_accepts_that_window(self):
        args = {'profileId': 'b', 'eventType': 'jdk.ExecutionSample', 'startEpochMs': 10, 'endEpochMs': 20}
        pattern = self.capture('flamegraph_export', args)
        self.assertFalse(self.violates(pattern, 'flamegraph_export', args))
        self.assertTrue(self.violates(pattern, 'flamegraph_export', {**args, 'startEpochMs': 11}))
        self.assertTrue(self.violates(pattern, 'flamegraph_export', {'profileId': 'b', 'eventType': 'jdk.ExecutionSample'}))

    def test_recorded_unweighted_variant_rejects_weighted_call(self):
        args = {'profileId': 'b', 'eventType': 'jdk.ObjectAllocationSample', 'useWeight': False}
        pattern = self.capture('flamegraph_export', args)
        self.assertFalse(self.violates(pattern, 'flamegraph_export', args))
        self.assertTrue(self.violates(pattern, 'flamegraph_export', {**args, 'useWeight': True}))
        self.assertTrue(self.violates(pattern, 'flamegraph_export', {'profileId': 'b', 'eventType': 'jdk.ObjectAllocationSample'}))

    def test_integral_float_inputs_match_javascript_number_serialization(self):
        for value in [1.0, -0.0, 1e-5, 1e-7, 1e21]:
            with self.subTest(value=value):
                args = {'profileId': 'b', 'eventType': 'jdk.ExecutionSample', 'thresholdPct': value}
                pattern = self.capture('flamegraph_export', args)
                self.assertFalse(self.violates(pattern, 'flamegraph_export', args))

    def test_optional_frame_prefixes_are_part_of_contract(self):
        schema = {'required': ['profileId'], 'properties': {'profileId': {'type': 'string'}, 'framePrefixes': {'type': 'array'}}}
        recorder.write_contract_graders([{'name': 'jvm_exceptionSites', 'inputSchema': schema}],
            [{'tool': 'jvm_exceptionSites', 'arguments': {'profileId': 'b', 'framePrefixes': ['com.acme.checkout.']}}])
        path = self.cases / 'example/graders/recorded-call-semantics.md'
        pattern = yaml.safe_load(path.read_text().split('---')[1])['pattern']
        self.assertFalse(self.violates(pattern, 'jvm_exceptionSites', {'profileId': 'b', 'framePrefixes': ['com.acme.checkout.']}))
        self.assertTrue(self.violates(pattern, 'jvm_exceptionSites', {'profileId': 'b', 'framePrefixes': ['java.']}))
        self.assertTrue(self.violates(pattern, 'jvm_exceptionSites', {'profileId': 'b'}))

    def test_async_heap_import_seeds_terminal_status(self):
        class SlowServer(recorder.Jeffrey):
            def __init__(self):
                self.polls = 0
            def call(self, tool, args):
                if tool == 'recordings_analyzeFile':
                    if args['name'] == 'checkout heap dump':
                        return answer({'operationId': 'slow-heap', 'status': 'RUNNING'})
                    return answer({'profileId': args['name']})
                if tool == 'operations_status':
                    self.polls += 1
                    if self.polls == 1:
                        return answer({'operationId': 'slow-heap', 'status': 'RUNNING'})
                    return answer({'operationId': 'slow-heap', 'status': 'COMPLETED', 'result': {'profileId': 'h'}})
                return answer({'status': 'COMPLETED'})
        recorded = recorder.Recorded()
        with patch.object(recorder.time, 'sleep'):
            profiles = recorder.import_profiles(SlowServer(), {k: Path(k) for k in ['baseline', 'candidate', 'heap']}, recorded)
        self.assertEqual('h', profiles.heap)
        self.assertIn('operations_status', recorded.answers, 'RUNNING import must have a replayable poll')
        self.assertEqual('COMPLETED', json.loads(recorded.answers['operations_status'][('slow-heap',)])['status'])


class CheckedInContractTest(unittest.TestCase):
    def test_every_checked_in_case_uses_current_contract(self):
        mocks = ROOT / 'jeffrey-claude-plugin/evals/mocks/jeffrey'
        cases = ROOT / 'jeffrey-claude-plugin/evals/cases'
        tools = json.loads((mocks / '_tools.json').read_text())['tools']
        inputs = json.loads((mocks / '_inputs.json').read_text())
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)
            (output / 'example/graders').mkdir(parents=True)
            with patch.object(recorder, 'MOCKED_CASES', output):
                recorder.write_contract_graders(tools, inputs)
            expected = (output / 'example/graders/recorded-call-semantics.md').read_text()
        for case in cases.iterdir():
            if (case / 'graders').is_dir():
                with self.subTest(case=case.name):
                    self.assertEqual(expected, (case / 'graders/recorded-call-semantics.md').read_text())

    def test_checked_in_inputs_and_weighting_counterexamples_in_javascript(self):
        mocks = ROOT / 'jeffrey-claude-plugin/evals/mocks/jeffrey'
        path = ROOT / 'jeffrey-claude-plugin/evals/cases/named-area-cpu/graders/recorded-call-semantics.md'
        pattern = yaml.safe_load(path.read_text().split('---')[1])['pattern']
        inputs = json.loads((mocks / '_inputs.json').read_text())
        tests = [{'call': {'tool': 'mcp__plugin_microscope_jeffrey__' + x['tool'], 'input': x['arguments']}, 'fails': False}
                 for x in inputs]
        for tool in ['flamegraph_export', 'compare_movements', 'compare_flamegraph']:
            item = next(x for x in inputs if x['tool'] == tool and x['arguments'].get('eventType') == 'jdk.ObjectAllocationSample')
            for change in [{'useWeight': False}, {'startEpochMs': 123}, {'excludeIdle': True}]:
                tests.append({'call': {'tool': 'mcp__plugin_microscope_jeffrey__' + tool,
                    'input': {**item['arguments'], **change}}, 'fails': True})
        result = subprocess.run(['node', '-e',
            'const x=JSON.parse(require("fs").readFileSync(0,"utf8")); const r=new RegExp(x.pattern,"m"); process.stdout.write(JSON.stringify(x.tests.map(t=>r.test(JSON.stringify(t.call)))));'],
            input=json.dumps({'pattern': pattern, 'tests': tests}), text=True, capture_output=True, check=True)
        actual = json.loads(result.stdout)
        for item, failed in zip(tests, actual, strict=True):
            with self.subTest(call=item['call']):
                self.assertEqual(item['fails'], failed)

    def test_timeline_defaults_remain_counted(self):
        schema = {'required': ['eventType'], 'properties': {'eventType': {'type': 'string'}, 'useWeight': {'type': 'boolean'}}}
        pattern = recorder.input_pattern('timeline_hotWindows', schema, {'eventType': 'jdk.ObjectAllocationSample'})
        self.assertIsNotNone(re.fullmatch(pattern, '{"eventType":"jdk.ObjectAllocationSample","useWeight":false}'))
        self.assertIsNone(re.fullmatch(pattern, '{"eventType":"jdk.ObjectAllocationSample","useWeight":true}'))


class VerdictContractTest(unittest.TestCase):
    def test_false_slowdown_is_explicitly_a_semantic_failure(self):
        graders = ROOT / 'jeffrey-claude-plugin/evals/cases/compare-finds-the-regression/graders'
        judges = [p for p in graders.glob('*.md') if yaml.safe_load(p.read_text().split('---')[1])['type'] == 'llm']
        self.assertTrue(judges, 'a semantic verdict requires an LLM rubric, not keyword presence')
        rubrics = '\n'.join(p.read_text() for p in judges)
        self.assertIn('High confidence: formatLine makes checkout 25 percent slower end-to-end. Both runs are directly comparable.', rubrics)
        self.assertIn('FAIL', rubrics)
        self.assertIn('PASS', rubrics)


if __name__ == '__main__':
    unittest.main()
