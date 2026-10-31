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
"""Tests the microscope plugin's SessionStart hook, jeffrey-status.sh, against a stand-in Jeffrey.

The hook runs at the start of every session in every repository the plugin is installed for, so what
it must not do matters as much as what it must: stay silent outside a JVM project and when Jeffrey is
up, and otherwise say in one SessionStart message why every tool is about to fail. Each test runs the
real script under bash, pointed at a local HTTP server that answers the way Jeffrey (or something
that is not Jeffrey) would, and checks what the hook printed and what it sent.

Usage: test-status-hook.py   (needs bash and curl, like the hook)
"""

import http.server
import json
import os
import shutil
import socket
import subprocess
import tempfile
import threading
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PLUGIN = ROOT / "jeffrey-claude-plugin"
HOOK = PLUGIN / "hooks" / "jeffrey-status.sh"
HOOKS_JSON = PLUGIN / "hooks" / "hooks.json"

PROTOCOL_VERSION = "2026-07-28"
DISCOVER_RESULT = {"jsonrpc": "2.0", "id": 0,
                   "result": {"supportedVersions": [PROTOCOL_VERSION], "resultType": "complete"}}

# Variables the hook reads; each test starts without any of them, so the runner's own never leak in.
HOOK_VARIABLES = ("CLAUDE_PLUGIN_OPTION_ENDPOINT_URL", "CLAUDE_PLUGIN_OPTION_TOKEN", "CLAUDE_PLUGIN_ROOT",
                  "JEFFREY_MCP_ENDPOINT", "JEFFREY_MCP_TOKEN", "CLAUDE_PROJECT_DIR", "GEMINI_PROJECT_DIR")
PROXY_VARIABLES = ("http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY", "all_proxy", "ALL_PROXY")


class StandIn(http.server.BaseHTTPRequestHandler):
    """Answers every POST with the server's (status, body) and keeps the request it was sent."""

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        self.server.requests.append({"path": self.path, "headers": dict(self.headers),
                                     "body": self.rfile.read(length).decode()})
        status, body = self.server.answer
        payload = body.encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *args):
        pass


def free_port() -> int:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


class HookTestCase(unittest.TestCase):

    def setUp(self):
        self.work = Path(tempfile.mkdtemp(prefix="jeffrey-status-hook-"))
        self.project = self.work / "project"
        self.project.mkdir()
        self.server = None

    def tearDown(self):
        if self.server:
            self.server.shutdown()
            self.server.server_close()
        shutil.rmtree(self.work, ignore_errors=True)

    def jeffrey(self, status: int, body) -> str:
        """Starts a stand-in answering `status` with `body`; returns its endpoint."""
        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), StandIn)
        self.server.requests = []
        self.server.answer = (status, body if isinstance(body, str) else json.dumps(body))
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        return f"http://127.0.0.1:{self.server.server_address[1]}/api/mcp"

    def jvm_project(self):
        (self.project / "pom.xml").write_text("<project/>")

    def run_hook(self, **variables) -> subprocess.CompletedProcess:
        env = {k: v for k, v in os.environ.items() if k not in HOOK_VARIABLES + PROXY_VARIABLES}
        env["NO_PROXY"] = env["no_proxy"] = "127.0.0.1,localhost"
        env["CLAUDE_PROJECT_DIR"] = str(self.project)
        env.update(variables)
        return subprocess.run(["bash", str(HOOK)], env=env, capture_output=True, text=True, timeout=30)

    def message(self, result: subprocess.CompletedProcess) -> str:
        """The one SessionStart message the hook printed, after checking it is the shape Claude Code reads."""
        self.assertEqual(0, result.returncode, result.stderr)
        output = json.loads(result.stdout)
        self.assertEqual("SessionStart", output["hookSpecificOutput"]["hookEventName"])
        return output["hookSpecificOutput"]["additionalContext"]

    def assertSilent(self, result: subprocess.CompletedProcess):
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("", result.stdout)


class WhenItSpeaks(HookTestCase):

    def test_silent_outside_a_jvm_project_even_when_jeffrey_is_down(self):
        endpoint = f"http://127.0.0.1:{free_port()}/api/mcp"
        self.assertSilent(self.run_hook(CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=endpoint))

    def test_a_build_file_at_the_root_makes_it_a_jvm_project(self):
        endpoint = f"http://127.0.0.1:{free_port()}/api/mcp"
        for build_file in ("pom.xml", "build.gradle.kts", "build.sbt"):
            with self.subTest(build_file=build_file):
                for existing in self.project.iterdir():
                    existing.unlink()
                (self.project / build_file).write_text("")
                self.assertIn("not answering", self.message(self.run_hook(CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=endpoint)))

    def test_a_recording_within_three_levels_counts_and_one_deeper_does_not(self):
        endpoint = f"http://127.0.0.1:{free_port()}/api/mcp"
        deep = self.project / "a" / "b" / "c"
        deep.mkdir(parents=True)
        (deep / "run.jfr").write_text("")
        self.assertSilent(self.run_hook(CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=endpoint))
        (self.project / "a" / "b" / "dump.hprof").write_text("")
        self.assertIn("not answering", self.message(self.run_hook(CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=endpoint)))

    def test_silent_when_jeffrey_answers_discover(self):
        self.jvm_project()
        self.assertSilent(self.run_hook(CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=self.jeffrey(200, DISCOVER_RESULT)))


class WhatItSends(HookTestCase):

    def test_a_discover_request_shaped_for_mcp_2026_07_28(self):
        self.jvm_project()
        self.run_hook(CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=self.jeffrey(200, DISCOVER_RESULT))
        request = self.server.requests[-1]
        headers = {k.lower(): v for k, v in request["headers"].items()}
        body = json.loads(request["body"])
        self.assertEqual("/api/mcp", request["path"])
        self.assertEqual(PROTOCOL_VERSION, headers["mcp-protocol-version"])
        self.assertEqual("server/discover", headers["mcp-method"])
        self.assertEqual("server/discover", body["method"])
        self.assertEqual(PROTOCOL_VERSION, body["params"]["_meta"]["io.modelcontextprotocol/protocolVersion"])
        self.assertNotIn("authorization", headers, "no token configured, so none is sent")

    def test_the_plugin_setting_wins_over_the_environment_for_the_endpoint(self):
        self.jvm_project()
        unused = f"http://127.0.0.1:{free_port()}/api/mcp"
        self.assertSilent(self.run_hook(CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=self.jeffrey(200, DISCOVER_RESULT),
                                        JEFFREY_MCP_ENDPOINT=unused))
        self.assertEqual(1, len(self.server.requests))

    def test_under_claude_code_only_the_plugin_token_is_sent(self):
        self.jvm_project()
        endpoint = self.jeffrey(200, DISCOVER_RESULT)
        self.run_hook(CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=endpoint, CLAUDE_PLUGIN_ROOT=str(PLUGIN),
                      JEFFREY_MCP_TOKEN="from-the-environment")
        self.assertNotIn("Authorization", self.server.requests[-1]["headers"],
                         "the plugin's server sends only its own token setting, so the probe must too")
        self.run_hook(CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=endpoint, CLAUDE_PLUGIN_OPTION_TOKEN="secret")
        self.assertEqual("Bearer secret", self.server.requests[-1]["headers"]["Authorization"])

    def test_elsewhere_the_environment_token_is_sent(self):
        self.jvm_project()
        self.run_hook(JEFFREY_MCP_ENDPOINT=self.jeffrey(200, DISCOVER_RESULT), JEFFREY_MCP_TOKEN="gemini-token")
        self.assertEqual("Bearer gemini-token", self.server.requests[-1]["headers"]["Authorization"])


class WhatItSays(HookTestCase):

    def test_nothing_answering_names_the_endpoint_and_where_to_change_it(self):
        self.jvm_project()
        endpoint = f"http://127.0.0.1:{free_port()}/api/mcp"
        text = self.message(self.run_hook(CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=endpoint))
        self.assertIn(f"Jeffrey is not answering at {endpoint}", text)
        self.assertIn("/plugin -> microscope", text)
        self.assertIn("tell the user", text)

    def test_a_401_without_a_token_says_one_is_required(self):
        self.jvm_project()
        text = self.message(self.run_hook(CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=self.jeffrey(401, {"error": "token"})))
        self.assertIn("requires a bearer token (HTTP 401) and this session sends none", text)
        self.assertIn("the plugin's Jeffrey MCP token setting", text, "the Claude Code wording, from the plugin option")

    def test_a_401_with_a_token_says_it_does_not_match(self):
        self.jvm_project()
        text = self.message(self.run_hook(JEFFREY_MCP_ENDPOINT=self.jeffrey(401, {"error": "token"}),
                                          JEFFREY_MCP_TOKEN="wrong"))
        self.assertIn("refused the token this session sends (HTTP 401)", text)
        self.assertIn("JEFFREY_MCP_TOKEN", text, "outside Claude Code, the variable to set")

    def test_a_403_quotes_the_servers_reason_as_valid_json(self):
        self.jvm_project()
        reason = 'Host "box:8585" is not in jeffrey.microscope.mcp.allowed-hosts \\ add it there'
        text = self.message(self.run_hook(CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=self.jeffrey(403, {"error": reason})))
        self.assertIn("refused this session (HTTP 403)", text)
        self.assertIn('Host "box:8585" is not in jeffrey.microscope.mcp.allowed-hosts', text)

    def test_a_200_that_is_not_a_discover_result_is_not_jeffrey(self):
        self.jvm_project()
        text = self.message(self.run_hook(CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=self.jeffrey(200, "<html>hello</html>")))
        self.assertIn("it is not a Jeffrey MCP server", text)
        self.assertIn("without a discover result", text)

    def test_another_status_is_named(self):
        self.jvm_project()
        text = self.message(self.run_hook(CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=self.jeffrey(500, {"error": "boom"})))
        self.assertIn("came back with HTTP 500", text)


class HowItIsStarted(HookTestCase):
    """The command hooks.json registers, as Claude Code runs it."""

    def command(self) -> str:
        return json.loads(HOOKS_JSON.read_text())["hooks"]["SessionStart"][0]["hooks"][0]["command"]

    def test_the_registered_command_runs_the_script_from_the_plugin_root(self):
        self.jvm_project()
        endpoint = f"http://127.0.0.1:{free_port()}/api/mcp"
        env = {k: v for k, v in os.environ.items() if k not in HOOK_VARIABLES + PROXY_VARIABLES}
        env.update(CLAUDE_PLUGIN_ROOT=str(PLUGIN), CLAUDE_PROJECT_DIR=str(self.project),
                   CLAUDE_PLUGIN_OPTION_ENDPOINT_URL=endpoint, NO_PROXY="127.0.0.1", no_proxy="127.0.0.1")
        result = subprocess.run(["sh", "-c", self.command()], env=env, capture_output=True, text=True, timeout=30)
        self.assertIn("not answering", self.message(result))

    def test_the_registered_command_is_silent_when_the_script_is_missing(self):
        env = {k: v for k, v in os.environ.items() if k not in HOOK_VARIABLES}
        env["CLAUDE_PLUGIN_ROOT"] = str(self.work)
        result = subprocess.run(["sh", "-c", self.command()], env=env, capture_output=True, text=True, timeout=30)
        self.assertSilent(result)

    def test_the_script_is_executable(self):
        self.assertTrue(os.access(HOOK, os.X_OK), "hooks.json runs it only when it is executable")


if __name__ == "__main__":
    unittest.main(verbosity=2)
