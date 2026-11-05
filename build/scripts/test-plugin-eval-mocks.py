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

from plugin_layout import PLUGIN_DIR

ROOT = Path(__file__).resolve().parents[2]
MOCKS = ROOT / PLUGIN_DIR / 'evals/mocks/jeffrey'
spec = importlib.util.spec_from_file_location('recorder', ROOT / 'build/scripts/record-plugin-eval-mocks.py')
recorder = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = recorder
spec.loader.exec_module(recorder)


def answer(value):
    return {'content': [{'type': 'text', 'text': json.dumps(value)}], 'structuredContent': value}


class RecorderSandbox(unittest.TestCase):
    """The recorder writing into a temporary mocks directory and one case, and the grader it writes, run as the
    harness runs it (JavaScript)."""

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

    def violates(self, pattern, tool, arguments):
        call = {'tool': 'mcp__plugin_microscope_jeffrey__' + tool, 'input': arguments, 'output': 'fixture'}
        result = subprocess.run(['node', '-e',
            'const x=JSON.parse(require("fs").readFileSync(0,"utf8")); process.stdout.write(String(new RegExp(x.pattern,"m").test(JSON.stringify(x.call))));'],
            input=json.dumps({'pattern': pattern, 'call': call}), text=True, capture_output=True, check=True)
        return result.stdout == 'true'


class ReplayContractTest(RecorderSandbox):
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
        with patch.object(recorder, 'seed_calls', return_value=[(tool, arguments)]), \
                patch.object(recorder, 'persons_seed_calls', return_value=[]):
            recorder.crawl(Server(), tools, recorder.Profiles('a', 'b', 'h', 'o', 'pd', 'pm'), recorded)
        recorder.write_mocks(tools, recorded)
        path = self.cases / 'example/graders/recorded-call-semantics.md'
        self.assertTrue(path.exists(), 'every case must reject unsupported semantic inputs')
        return yaml.safe_load(path.read_text().split('---')[1])['pattern']

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

    def test_size_detail_and_threshold_do_not_change_what_the_answer_says(self):
        args = {'profileId': 'b', 'eventType': 'jdk.ExecutionSample'}
        pattern = self.capture('flamegraph_export', args)
        for change in [{'thresholdPct': 2.5}, {'detail': 'SUMMARY'}, {'useWeight': False}]:
            with self.subTest(change=change):
                self.assertFalse(self.violates(pattern, 'flamegraph_export', {**args, **change}))

    def test_a_list_filter_or_page_size_is_not_graded(self):
        schema = {'required': [], 'properties': {'search': {'type': 'string'},
                                                 'limit': {'type': 'integer', 'default': 100}}}
        heap = {'required': ['profileId'], 'properties': {'profileId': {'type': 'string'}, 'topN': {'type': 'integer'},
                                                         'sortBy': {'type': 'string'}}}
        recorder.write_contract_graders(
            [{'name': 'profiles_list', 'inputSchema': schema}, {'name': 'heap_getClassHistogram', 'inputSchema': heap}],
            [{'tool': 'profiles_list', 'arguments': {}}, {'tool': 'heap_getClassHistogram', 'arguments': {'profileId': 'h'}}])
        path = self.cases / 'example/graders/recorded-call-semantics.md'
        pattern = yaml.safe_load(path.read_text().split('---')[1])['pattern']
        self.assertFalse(self.violates(pattern, 'profiles_list', {'search': 'checkout', 'limit': 10}))
        self.assertFalse(self.violates(pattern, 'heap_getClassHistogram', {'profileId': 'h', 'topN': 12, 'sortBy': 'COUNT'}))

    def test_whitespace_after_the_input_key_cannot_slip_a_call_past(self):
        args = {'profileId': 'b', 'eventType': 'jdk.ObjectAllocationSample'}
        pattern = self.capture('flamegraph_export', args)
        spaced = '{"tool":"mcp__plugin_microscope_jeffrey__flamegraph_export","input": %s}'
        self.assertIsNone(re.search(pattern, spaced % json.dumps(args), re.M), 'the recorded call passes')
        self.assertIsNotNone(re.search(pattern, spaced % json.dumps({**args, 'useWeight': False}), re.M))

    def test_a_value_holding_braces_is_still_read_as_one_value(self):
        schema = {'required': ['profileId', 'name'], 'properties': {'profileId': {'type': 'string'},
                  'name': {'type': 'string'}, 'framePrefixes': {'type': 'array'}}}
        recorded = {'profileId': 'o', 'name': 'GET /orders/{customer}', 'framePrefixes': ['com.acme.']}
        recorder.write_contract_graders([{'name': 'traces_slowestTraces', 'inputSchema': schema}],
                                        [{'tool': 'traces_slowestTraces', 'arguments': recorded}])
        path = self.cases / 'example/graders/recorded-call-semantics.md'
        pattern = yaml.safe_load(path.read_text().split('---')[1])['pattern']
        self.assertFalse(self.violates(pattern, 'traces_slowestTraces', recorded))
        self.assertTrue(self.violates(pattern, 'traces_slowestTraces', {**recorded, 'framePrefixes': ['org.']}))
        # A key spelled inside another value is not the key.
        self.assertTrue(self.violates(pattern, 'traces_slowestTraces',
                                      {'profileId': 'o', 'name': 'GET /orders/{customer}',
                                       'note': '"framePrefixes":["com.acme."]'}))

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
            profiles = recorder.import_profiles(SlowServer(), {k: Path(k) for k in recorder.PROFILE_KINDS}, recorded)
        self.assertEqual('h', profiles.heap)
        self.assertIn('operations_status', recorded.answers, 'RUNNING import must have a replayable poll')
        self.assertEqual('COMPLETED', json.loads(recorded.answers['operations_status'][('slow-heap',)])['status'])


class CodeToolsTest(RecorderSandbox):
    """The code tools: a method addresses its fixture beside the class, and a call that differs from the recorded
    one only in how the own packages were chosen is accepted when its answer carries the same figures."""

    METHOD_SCHEMA = {'type': 'object', 'required': ['className', 'profileId'], 'properties': {
        'profileId': {'type': 'string'}, 'className': {'type': 'string'}, 'methodName': {'type': 'string'},
        'eventType': {'type': 'string'}, 'framePrefixes': {'type': 'array'}}}
    HOTSPOT_SCHEMA = {'type': 'object', 'required': ['eventType', 'profileId'], 'properties': {
        'profileId': {'type': 'string'}, 'eventType': {'type': 'string'}, 'useWeight': {'type': 'boolean'},
        'framePrefixes': {'type': 'array'}}}

    def record(self, tool, schema, calls, respond):
        tools = [{'name': tool, 'inputSchema': schema}]
        recorded = recorder.Recorded()
        class Server:
            def call(self, name, args):
                return answer(respond(args))
        with patch.object(recorder, 'seed_calls', return_value=[(tool, arguments) for arguments in calls]), \
                patch.object(recorder, 'persons_seed_calls', return_value=[]):
            recorder.crawl(Server(), tools, recorder.Profiles('a', 'b', 'h', 'o', 'pd', 'pm'), recorded)
        recorder.write_mocks(tools, recorded)
        path = self.cases / 'example/graders/recorded-call-semantics.md'
        return recorded, yaml.safe_load(path.read_text().split('---')[1])['pattern']

    def test_each_method_of_a_class_is_its_own_fixture(self):
        own = {'framePrefixes': ['com.acme.']}
        calls = [{'profileId': 'b', 'className': 'com.acme.Checkout.Cache', 'methodName': method, **own}
                 for method in ('touch', 'busyWait')]
        calls.append({'profileId': 'b', 'className': 'com.acme.Checkout.Cache', **own})
        self.record('code_method', self.METHOD_SCHEMA, calls, lambda args: {'method': args.get('methodName')})
        self.assertIn('{input.className}-{input.methodName}-{input.profileId}', (self.mocks / 'code_method.md').read_text())
        fixtures = sorted(path.name for path in (self.mocks / 'fixtures').iterdir())
        self.assertEqual(['code_method-com.acme.Checkout.Cache-busyWait-b.txt',
                          'code_method-com.acme.Checkout.Cache-touch-b.txt'], fixtures)

    def test_suggested_prefixes_with_the_same_figures_are_accepted(self):
        given = {'profileId': 'b', 'eventType': 'jdk.ExecutionSample', 'framePrefixes': ['com.acme.checkout.']}
        suggested = {'profileId': 'b', 'eventType': 'jdk.ExecutionSample'}
        def respond(args):
            prefixes = args.get('framePrefixes')
            return {'framePrefixes': prefixes or ['com.acme.'], 'framePrefixesSource': 'GIVEN' if prefixes else 'SUGGESTED',
                    'charged': 10, 'followUp': {'nextTools': [], 'guidance': [] if prefixes else ['check them']}}
        recorded, pattern = self.record('code_hotspots', self.HOTSPOT_SCHEMA, [given, suggested], respond)
        self.assertFalse(self.violates(pattern, 'code_hotspots', given))
        self.assertFalse(self.violates(pattern, 'code_hotspots', suggested))
        self.assertTrue(self.violates(pattern, 'code_hotspots', {**given, 'framePrefixes': ['org.other.']}))
        inputs = json.loads((self.mocks / '_inputs.json').read_text())
        self.assertEqual([given], [entry['equivalentTo'] for entry in inputs if 'equivalentTo' in entry])

    def test_other_figures_are_not_accepted_with_the_fixture(self):
        given = {'profileId': 'b', 'eventType': 'jdk.ExecutionSample', 'framePrefixes': ['com.acme.checkout.']}
        suggested = {'profileId': 'b', 'eventType': 'jdk.ExecutionSample'}
        _, pattern = self.record('code_hotspots', self.HOTSPOT_SCHEMA, [given, suggested],
                                 lambda args: {'charged': 10 if args.get('framePrefixes') else 12})
        self.assertTrue(self.violates(pattern, 'code_hotspots', suggested))

    def test_the_weighting_follows_the_event_type_as_the_flamegraph_does(self):
        args = {'profileId': 'b', 'eventType': 'jdk.ObjectAllocationSample', 'framePrefixes': ['com.acme.']}
        _, pattern = self.record('code_hotspots', self.HOTSPOT_SCHEMA, [args], lambda _: {'charged': 1})
        self.assertFalse(self.violates(pattern, 'code_hotspots', {**args, 'useWeight': True}))
        self.assertTrue(self.violates(pattern, 'code_hotspots', {**args, 'useWeight': False}))


class AddressingTest(unittest.TestCase):
    """Which required arguments name a fixture, and which are pinned by the mock's expect: list."""

    REQUIRED = ['eventType', 'kind', 'name', 'profileId']

    def test_an_operation_name_is_pinned_while_the_safe_arguments_tell_the_answers_apart(self):
        answers = {('jeffrey.HttpServerExchange', 'SERVER', 'POST /checkout', 'baseline'): 'a',
                   ('jeffrey.HttpServerExchange', 'SERVER', 'POST /checkout', 'candidate'): 'b'}
        self.assertEqual(['eventType', 'kind', 'profileId'], recorder.addressed(self.REQUIRED, answers))

    def test_free_text_with_several_values_per_address_cannot_be_pinned(self):
        answers = {('jeffrey.HttpServerExchange', 'SERVER', 'POST /checkout', 'candidate'): 'a',
                   ('jeffrey.HttpServerExchange', 'SERVER', 'GET /orders/{id}', 'candidate'): 'b'}
        self.assertIsNone(recorder.addressed(self.REQUIRED, answers))

    def test_a_query_the_agent_writes_is_never_pinned(self):
        answers = {('candidate', 'SELECT * FROM events LIMIT 10'): 'a'}
        self.assertIsNone(recorder.addressed(['profileId', 'query'], answers))

    def test_safe_values_address_the_fixture_whole(self):
        answers = {('jdk.ExecutionSample', 'SERVER', 'checkout', 'candidate'): 'a'}
        self.assertEqual(self.REQUIRED, recorder.addressed(self.REQUIRED, answers))

    def test_the_recorded_mocks_pin_only_with_a_list_of_recorded_values(self):
        for mock in sorted(MOCKS.glob('*.md')):
            front = yaml.safe_load(mock.read_text().split('---')[1])
            for argument, allowed in (front.get('expect') or {}).items():
                with self.subTest(mock=mock.name, argument=argument):
                    if mock.stem == 'recordings_analyzeFile':
                        self.assertIsInstance(allowed, str)
                    else:
                        self.assertIsInstance(allowed, list)
                        self.assertNotIn('{input.' + argument + '}', mock.read_text())


class CheckedInContractTest(unittest.TestCase):
    def test_every_checked_in_case_uses_current_contract(self):
        mocks = ROOT / PLUGIN_DIR / 'evals/mocks/jeffrey'
        cases = ROOT / PLUGIN_DIR / 'evals/cases'
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
        mocks = ROOT / PLUGIN_DIR / 'evals/mocks/jeffrey'
        path = ROOT / PLUGIN_DIR / 'evals/cases/gc-pauses/graders/recorded-call-semantics.md'
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


class AnswerSizeTest(unittest.TestCase):
    """A tool that declares no large result is held by the client to its default limit (Claude Code:
    25,000 tokens), past which the answer is saved where the agent may not read it; the server keeps such
    answers under McpToolOutput.UNDECLARED_MAX_CHARS. The mocks are a real Jeffrey's answers to every call
    the recorder reaches, so a tool that outgrows it shows here on the next re-record."""

    UNDECLARED_MAX_CHARS = 50_000

    def test_every_recorded_answer_of_an_undeclared_tool_fits_the_clients_limit(self):
        mocks = ROOT / PLUGIN_DIR / 'evals/mocks/jeffrey'
        tools = json.loads((mocks / '_tools.json').read_text())['tools']
        declared = {tool['name'] for tool in tools
                    if (tool.get('_meta') or {}).get('anthropic/maxResultSizeChars')}
        undeclared = {tool['name'] for tool in tools} - declared
        oversized = []
        for fixture in list((mocks / 'fixtures').glob('*.txt')) + list(mocks.glob('*.md')):
            tool = next((name for name in undeclared
                         if fixture.stem == name or fixture.name.startswith(name + '-')), None)
            if tool is None:
                continue
            body = fixture.read_text()
            if fixture.suffix == '.md':
                body = body.split('---', 2)[-1].strip()
            if len(body) > self.UNDECLARED_MAX_CHARS:
                oversized.append(f'{fixture.name}: {len(body)}')
        self.assertEqual([], oversized)


class ScaffoldTest(unittest.TestCase):
    """Every case that scaffolds the checkout repository embeds the recorded workloads, byte for byte, and the
    live ones build the history the live recordings were imported at."""

    WORKLOADS = {'CHECKOUT_JAVA': 'Checkout.java', 'ORDER_HISTORY_JAVA': 'OrderHistory.java'}
    LIVE = ROOT / PLUGIN_DIR / 'evals-live/cases'

    @staticmethod
    def scaffolds():
        suites = (ROOT / PLUGIN_DIR / 'evals/cases', ROOT / PLUGIN_DIR / 'evals-live/cases')
        return sorted((script for cases in suites for script in cases.glob('*/setup.sh')
                       if "<<'CHECKOUT_JAVA'" in script.read_text()), key=lambda script: script.parent.name)

    @staticmethod
    def scaffold(script, temp):
        subprocess.run(['bash', str(script)], cwd=temp, check=True, capture_output=True)

    @staticmethod
    def git(temp, *arguments):
        return subprocess.run(['git', *arguments], cwd=temp, check=True, capture_output=True, text=True).stdout

    def test_the_implement_fixes_and_code_cases_scaffold_the_repository(self):
        self.assertEqual(['code-n-plus-one', 'compare-the-methods-my-diff-touched',
                          'did-my-last-commit-make-it-worse', 'heap-offers-implement-fixes'],
                         [script.parent.name for script in self.scaffolds()])

    def test_the_scaffolded_repository_holds_the_recorded_workloads_verbatim(self):
        for marker, name in self.WORKLOADS.items():
            workload = (ROOT / PLUGIN_DIR / 'evals/fixtures/workload/com/acme/checkout' / name).read_text()
            for script in self.scaffolds():
                with self.subTest(case=script.parent.name, workload=name):
                    embedded = re.search(rf"<<'{marker}'\n(.*?)^{marker}$", script.read_text(), re.S | re.M)
                    self.assertIsNotNone(embedded, f'setup.sh no longer embeds {name} between its markers')
                    self.assertEqual(workload if workload.endswith('\n') else workload + '\n', embedded.group(1))

    def test_the_scaffold_builds_a_repository_with_the_classes_the_profiles_name(self):
        for script in self.scaffolds():
            with self.subTest(case=script.parent.name), tempfile.TemporaryDirectory() as temp:
                self.scaffold(script, temp)
                checkout = Path(temp) / 'src/main/java/com/acme/checkout'
                self.assertIn('busyWait', (checkout / 'Checkout.java').read_text())
                self.assertIn('class AuditLog', (checkout / 'Checkout.java').read_text())
                self.assertIn('findLines', (checkout / 'OrderHistory.java').read_text())
                self.assertRegex(self.git(temp, 'rev-parse', 'HEAD').strip(), r'^[0-9a-f]{40}$')
                self.assertEqual('', self.git(temp, 'status', '--porcelain'))

    def test_the_live_history_is_the_one_the_live_recordings_were_imported_at(self):
        commits = json.loads((ROOT / PLUGIN_DIR / 'evals/fixtures/recorded-profiles.json').read_text())['liveCommits']
        heads = {'compare-the-methods-my-diff-touched': 'candidate', 'did-my-last-commit-make-it-worse': None}
        for case, head in heads.items():
            with self.subTest(case=case), tempfile.TemporaryDirectory() as temp:
                self.scaffold(self.LIVE / case / 'setup.sh', temp)
                log = self.git(temp, 'log', '--format=%H').split()
                self.assertEqual([commits['candidate'], commits['baseline']], log[-2:])
                if head:
                    self.assertEqual(commits[head], log[0])
                else:
                    self.assertNotIn(log[0], commits.values(), 'the checkout has moved past the profiled commit')
                diff = self.git(temp, 'diff', commits['baseline'], commits['candidate'])
                self.assertIn('+                    total += formatLine(line).length();', diff)
                self.assertEqual(3, sum(1 for line in diff.splitlines() if line.startswith('+ ')))

class PersonsScaffoldTest(unittest.TestCase):
    """Every persons case that works on the code scaffolds the persons service's repository: the sources the two
    real runs were built from (fixtures/testapp/), each byte for byte between its marker, committed."""

    SOURCES = ROOT / PLUGIN_DIR / 'evals/fixtures/testapp'
    CASES = ROOT / PLUGIN_DIR / 'evals/cases'

    @classmethod
    def scaffolds(cls):
        return sorted((script for script in cls.CASES.glob('*/setup.sh') if "<<'TESTAPP_" in script.read_text()),
                      key=lambda script: script.parent.name)

    def test_the_persons_code_cases_scaffold_the_repository(self):
        self.assertEqual(['fix-it', 'hot-methods', 'method-worth-it', 'what-runs'],
                         [script.parent.name for script in self.scaffolds()])

    def test_the_scaffold_is_the_one_the_writer_writes(self):
        spec = importlib.util.spec_from_file_location('scaffold', ROOT / 'build/scripts/write-persons-scaffold.py')
        writer = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(writer)
        for script in self.scaffolds():
            with self.subTest(case=script.parent.name):
                self.assertEqual(writer.script(), script.read_text(),
                                 'run python3 build/scripts/write-persons-scaffold.py')

    def test_the_scaffolded_repository_holds_the_sources_verbatim(self):
        sources = sorted(path.relative_to(self.SOURCES) for path in self.SOURCES.rglob('*') if path.is_file())
        for script in self.scaffolds():
            with self.subTest(case=script.parent.name), tempfile.TemporaryDirectory() as temp:
                subprocess.run(['bash', str(script)], cwd=temp, check=True, capture_output=True)
                written = sorted(path.relative_to(temp) for path in Path(temp).rglob('*')
                                 if path.is_file() and '.git' not in path.relative_to(temp).parts)
                self.assertEqual(sources, written)
                for source in sources:
                    self.assertEqual((self.SOURCES / source).read_bytes(), (Path(temp) / source).read_bytes(), str(source))
                self.assertIn('class InefficientPersonService',
                              (Path(temp) / 'server/src/main/java/jeffrey/testapp/server/service/InefficientPersonService.java').read_text())
                status = subprocess.run(['git', 'status', '--porcelain'], cwd=temp, check=True, capture_output=True, text=True)
                self.assertEqual('', status.stdout)


class VerdictContractTest(unittest.TestCase):
    def test_false_slowdown_is_explicitly_a_semantic_failure(self):
        graders = ROOT / PLUGIN_DIR / 'evals/cases/dom-regression/graders'
        judges = [p for p in graders.glob('*.md') if yaml.safe_load(p.read_text().split('---')[1])['type'] == 'llm']
        self.assertTrue(judges, 'a semantic verdict requires an LLM rubric, not keyword presence')
        rubrics = '\n'.join(p.read_text() for p in judges)
        self.assertIn('High confidence: the new build makes the persons service 25 percent slower end-to-end because of the '
                      'JSON tree. Both runs are directly comparable.', rubrics)
        self.assertIn('FAIL', rubrics)
        self.assertIn('PASS', rubrics)


if __name__ == '__main__':
    unittest.main()
