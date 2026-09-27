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
"""Normalises an MCP `tools/list` response into a stable, diffable snapshot.

`tools/list` carries free-text descriptions that change every time someone rewords a
tool for an agent, which makes the raw response useless as a CI gate: every wording
pass would "break the build". What actually matters for API compatibility is the
shape — tool names, `title`, `annotations`, whether a parameter is required, its
type/enum/default/bounds, the output schema a tool declares (its content, with every
`description` dropped and `required` sorted), and its `_meta` keys (plus the value of the
keys that are contract: every `jeffrey/*` hint and `anthropic/maxResultSizeChars`). This
script reduces `tools/list` down to exactly that, sorted so two runs of the same server
produce byte-identical output regardless of map/array ordering on the wire (enum order is
the one exception, kept as declared).

Usage:
    # Regenerate the snapshot from a running Microscope (writes to stdout):
    python3 mcp-snapshot.py --url http://localhost:8585/api/mcp > build/mcp/tools-list.snapshot.json

    # Same, but read the raw tools/list response (a JSON-RPC envelope or a bare result) from stdin:
    curl -s http://localhost:8585/api/mcp -H 'Content-Type: application/json' \\
        -H 'MCP-Protocol-Version: 2026-07-28' -H 'Mcp-Method: tools/list' \\
        -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{
              "io.modelcontextprotocol/protocolVersion":"2026-07-28",
              "io.modelcontextprotocol/clientCapabilities":{}}}}' \\
      | python3 mcp-snapshot.py > build/mcp/tools-list.snapshot.json

    # CI gate: fail with a unified diff if the live server drifted from the snapshot.
    python3 mcp-snapshot.py --url http://localhost:8585/api/mcp --check

    # Offline sanity check: verify normalize() against the checked-in fixture.
    python3 mcp-snapshot.py --self-test

`--url` sends its own `tools/list` POST shaped the way MCP `2026-07-28` requires -- the version
and client capabilities in `params._meta`, repeated in the `MCP-Protocol-Version` and
`Mcp-Method` headers -- so the check step needs no npx/Node. Jeffrey speaks that revision only:
there is no `initialize` handshake to open with, and a request without `_meta` is refused.

Before normalising, the result is also held to the envelope `2026-07-28` defines for a list:
`resultType` is `complete`, the cache hint (`ttlMs` >= 0, `cacheScope` public|private) is present,
and `_meta["io.modelcontextprotocol/serverInfo"].name` is `jeffrey`. Those fields sit beside the
tool list, so they are checked rather than snapshotted: the snapshot itself is the tools only.
"""

import argparse
import difflib
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DEFAULT_SNAPSHOT = REPO_ROOT / "build" / "mcp" / "tools-list.snapshot.json"
FIXTURE_PATH = REPO_ROOT / "build" / "mcp" / "tools-list.fixture.json"
FIXTURE_EXPECTED_PATH = REPO_ROOT / "build" / "mcp" / "tools-list.fixture.expected.json"

# The only protocol revision Jeffrey's MCP server speaks. Every request carries it twice: in
# params._meta and in the MCP-Protocol-Version header; a mismatch or an absence is refused.
MCP_PROTOCOL_VERSION = "2026-07-28"
TOOLS_LIST_METHOD = "tools/list"
REQUEST_TIMEOUT_SECONDS = 30

PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version"
METHOD_HEADER = "Mcp-Method"
META_FIELD = "_meta"
META_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion"
META_CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities"
META_CLIENT_INFO = "io.modelcontextprotocol/clientInfo"
META_SERVER_INFO = "io.modelcontextprotocol/serverInfo"
CLIENT_INFO = {"name": "jeffrey-mcp-snapshot", "version": "1"}

# What a complete list result must carry besides its tools (2026-07-28: every result names its
# kind in resultType; list results are cacheable and MUST carry ttlMs + cacheScope).
RESULT_TYPE_FIELD = "resultType"
RESULT_TYPE_COMPLETE = "complete"
TTL_FIELD = "ttlMs"
CACHE_SCOPE_FIELD = "cacheScope"
CACHE_SCOPES = frozenset({"public", "private"})
EXPECTED_SERVER_NAME = "jeffrey"

# The only per-property fields captured in the snapshot. Anything else on a property
# (title, description, examples, ...) is prose/metadata, not contract. By design this also
# excludes an array property's `items` schema, so a change to an array's element type/enum
# is not caught here.
PROPERTY_FIELDS = ("type", "enum", "default", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum")

# `_meta` keys whose *value* is contract, not just their presence: the response-size cap the
# server advertises to the model, and every key under Jeffrey's own `jeffrey/` prefix (the
# per-tool hints such as `jeffrey/cost` and `jeffrey/requires`). Any other `_meta` key is captured
# by name only: its value is free to be internal bookkeeping without becoming a snapshot diff.
META_VALUE_KEYS = ("anthropic/maxResultSizeChars",)
META_VALUE_PREFIX = "jeffrey/"

# The output-schema keyword dropped from the snapshot: prose, like a tool's description, that is
# reworded for agents without changing the contract. Only the keyword is dropped — a property that
# happens to be *named* `description` is part of the shape and stays.
SCHEMA_DESCRIPTION = "description"
# Output-schema keywords whose value is itself a subschema, or a map of them.
SCHEMA_SUBSCHEMA_KEYWORDS = ("items", "additionalProperties")
SCHEMA_PROPERTIES = "properties"
SCHEMA_REQUIRED = "required"


class SnapshotError(RuntimeError):
    """Raised for any input/network problem; caught once in main() and reported cleanly."""


def normalize_property(raw_property):
    normalized = {}
    for field in PROPERTY_FIELDS:
        if field in raw_property:
            # enum order is preserved deliberately (controller ruling): it is the order the
            # model sees the choices in, so a reordering is real, visible drift, not noise.
            normalized[field] = raw_property[field]
    return normalized


def normalize_input_schema(raw_schema):
    raw_schema = raw_schema or {}
    raw_properties = raw_schema.get("properties") or {}
    properties = {name: normalize_property(value) for name, value in raw_properties.items()}
    required = sorted(raw_schema.get("required") or [])
    return {
        "properties": properties,
        "required": required,
        "additionalProperties": raw_schema.get("additionalProperties"),
    }


def normalize_meta(raw_meta):
    normalized = {}
    for key in raw_meta:
        keeps_value = key in META_VALUE_KEYS or key.startswith(META_VALUE_PREFIX)
        normalized[key] = raw_meta[key] if keeps_value else None
    return normalized


def normalize_output_schema(raw_schema):
    """The schema's content without its prose: `description` is dropped at every level and
    `required` is sorted, the rest is kept as is (object keys are sorted when the snapshot is
    written; `enum` and a `type` list keep their declared order)."""
    if not isinstance(raw_schema, dict):
        # A boolean subschema (`"additionalProperties": false`) is contract as it stands.
        return raw_schema
    normalized = {}
    for keyword, value in raw_schema.items():
        if keyword == SCHEMA_DESCRIPTION:
            continue
        if keyword == SCHEMA_PROPERTIES and isinstance(value, dict):
            normalized[keyword] = {name: normalize_output_schema(child) for name, child in value.items()}
        elif keyword in SCHEMA_SUBSCHEMA_KEYWORDS:
            normalized[keyword] = normalize_output_schema(value)
        elif keyword == SCHEMA_REQUIRED and isinstance(value, list):
            normalized[keyword] = sorted(value)
        else:
            normalized[keyword] = value
    return normalized


def normalize_tool(raw_tool):
    normalized = {"name": raw_tool["name"]}
    # `title` is a top-level tool field (not inside `annotations`), derived from the name on
    # the server; captured here so a changed derivation shows up as drift.
    if raw_tool.get("title"):
        normalized["title"] = raw_tool["title"]
    if raw_tool.get("annotations"):
        normalized["annotations"] = raw_tool["annotations"]
    normalized["inputSchema"] = normalize_input_schema(raw_tool.get("inputSchema"))
    if raw_tool.get("outputSchema") is not None:
        normalized["outputSchema"] = normalize_output_schema(raw_tool["outputSchema"])
    meta = raw_tool.get("_meta")
    if meta:
        normalized["_meta"] = normalize_meta(meta)
    return normalized


def normalize_tools_list(raw_result):
    raw_tools = raw_result.get("tools") or []
    tools = sorted((normalize_tool(tool) for tool in raw_tools), key=lambda tool: tool["name"])
    return {"tools": tools}


def check_result_envelope(raw_result):
    """Holds a tools/list result to the 2026-07-28 envelope: raises SnapshotError naming the first
    field that is missing or wrong. The tools themselves are left to normalize_tools_list()."""
    if not isinstance(raw_result, dict):
        raise SnapshotError(f"tools/list result is not a JSON object: {raw_result!r}")

    result_type = raw_result.get(RESULT_TYPE_FIELD)
    if result_type != RESULT_TYPE_COMPLETE:
        raise SnapshotError(f"tools/list result has {RESULT_TYPE_FIELD}={result_type!r}, expected {RESULT_TYPE_COMPLETE!r}")

    ttl = raw_result.get(TTL_FIELD)
    if isinstance(ttl, bool) or not isinstance(ttl, int) or ttl < 0:
        raise SnapshotError(f"tools/list result has {TTL_FIELD}={ttl!r}, expected an integer >= 0")

    scope = raw_result.get(CACHE_SCOPE_FIELD)
    if scope not in CACHE_SCOPES:
        raise SnapshotError(f"tools/list result has {CACHE_SCOPE_FIELD}={scope!r}, expected one of {sorted(CACHE_SCOPES)}")

    meta = raw_result.get(META_FIELD)
    server_info = meta.get(META_SERVER_INFO) if isinstance(meta, dict) else None
    server_name = server_info.get("name") if isinstance(server_info, dict) else None
    if server_name != EXPECTED_SERVER_NAME:
        raise SnapshotError(
            f"tools/list result has {META_FIELD}[{META_SERVER_INFO!r}].name={server_name!r}, "
            f"expected {EXPECTED_SERVER_NAME!r}"
        )


def to_snapshot_text(normalized):
    return json.dumps(normalized, indent=2, sort_keys=True) + "\n"


def parse_jsonrpc_body(body, content_type):
    """Streamable HTTP responses may come back as a single SSE frame instead of plain JSON."""
    if "text/event-stream" in content_type:
        data_lines = [line[len("data:"):].strip() for line in body.splitlines() if line.startswith("data:")]
        if not data_lines:
            raise SnapshotError("no 'data:' lines found in the text/event-stream response")
        return json.loads(data_lines[-1])
    return json.loads(body)


def tools_list_request_body():
    meta = {
        META_PROTOCOL_VERSION: MCP_PROTOCOL_VERSION,
        META_CLIENT_CAPABILITIES: {},
        META_CLIENT_INFO: CLIENT_INFO,
    }
    return {"jsonrpc": "2.0", "id": 1, "method": TOOLS_LIST_METHOD, "params": {META_FIELD: meta}}


def fetch_tools_list(url):
    payload = json.dumps(tools_list_request_body()).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=payload,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json, text/event-stream",
            PROTOCOL_VERSION_HEADER: MCP_PROTOCOL_VERSION,
            METHOD_HEADER: TOOLS_LIST_METHOD,
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            content_type = response.headers.get("Content-Type", "")
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        # A 4xx on this path carries a JSON-RPC error worth printing: a -32602 or -32022 names the
        # revision the server speaks in data.supported, a -32020 the header it disagreed with.
        detail = exc.read().decode("utf-8", errors="replace")
        raise SnapshotError(f"{url} answered HTTP {exc.code}: {detail}") from exc
    except (urllib.error.URLError, TimeoutError) as exc:
        raise SnapshotError(f"could not reach {url}: {exc}") from exc

    envelope = parse_jsonrpc_body(body, content_type)
    if "error" in envelope:
        raise SnapshotError(f"tools/list returned a JSON-RPC error: {envelope['error']}")
    if "result" not in envelope:
        raise SnapshotError(f"tools/list response had neither 'result' nor 'error': {envelope!r}")
    return envelope["result"]


def read_raw_result(args):
    if args.url:
        return fetch_tools_list(args.url)

    text = sys.stdin.read()
    if not text.strip():
        raise SnapshotError("no input: pass --url, or pipe the raw tools/list JSON on stdin")
    try:
        envelope = json.loads(text)
    except json.JSONDecodeError as exc:
        raise SnapshotError(f"stdin was not valid JSON: {exc}") from exc

    # Accept either a bare tools/list result ({"tools": [...]}) or a full JSON-RPC envelope
    # ({"jsonrpc": ..., "result": {"tools": [...]}}) so curl/npx output can be piped in as-is.
    result = envelope.get("result") if isinstance(envelope, dict) else None
    if isinstance(result, dict) and "tools" in result:
        return result
    return envelope


def run_check(normalized_text, snapshot_path):
    if not snapshot_path.exists():
        print(f"snapshot not found: {snapshot_path}", file=sys.stderr)
        return 1

    existing_text = snapshot_path.read_text()
    if existing_text == normalized_text:
        print(f"OK: tools/list matches {snapshot_path}")
        return 0

    diff = difflib.unified_diff(
        existing_text.splitlines(keepends=True),
        normalized_text.splitlines(keepends=True),
        fromfile=str(snapshot_path),
        tofile="tools/list (current)",
    )
    sys.stderr.writelines(diff)
    print(f"\nFAIL: tools/list drifted from {snapshot_path}", file=sys.stderr)
    return 1


def run_self_test():
    """Verifies normalize_tools_list() against a checked-in fixture, no network or Microscope
    needed. This is the fast, offline way to sanity-check the script itself."""
    missing = [path for path in (FIXTURE_PATH, FIXTURE_EXPECTED_PATH) if not path.exists()]
    if missing:
        for path in missing:
            print(f"self-test fixture missing: {path}", file=sys.stderr)
        return 1

    raw = json.loads(FIXTURE_PATH.read_text())
    envelope_failures = self_test_envelope(raw)
    if envelope_failures:
        for failure in envelope_failures:
            print(f"FAIL: self-test — {failure}", file=sys.stderr)
        return 1

    actual_text = to_snapshot_text(normalize_tools_list(raw))
    expected_text = FIXTURE_EXPECTED_PATH.read_text()

    if actual_text == expected_text:
        print(f"OK: self-test — {FIXTURE_PATH.name} passes the 2026-07-28 envelope check, "
              f"and each broken copy of it is refused")
        print(f"OK: self-test — normalize({FIXTURE_PATH.name}) matches {FIXTURE_EXPECTED_PATH.name}")
        return 0

    diff = difflib.unified_diff(
        expected_text.splitlines(keepends=True),
        actual_text.splitlines(keepends=True),
        fromfile=str(FIXTURE_EXPECTED_PATH),
        tofile="normalize(fixture) (actual)",
    )
    sys.stderr.writelines(diff)
    print("\nFAIL: self-test — normalization does not match the expected fixture", file=sys.stderr)
    return 1


def self_test_envelope(raw):
    """The fixture must pass check_result_envelope(), and each copy of it with one envelope field
    broken must be refused; returns what went wrong, empty when all of that held."""
    failures = []
    try:
        check_result_envelope(raw)
    except SnapshotError as exc:
        failures.append(f"the fixture itself fails the envelope check: {exc}")

    broken_copies = {
        f"{RESULT_TYPE_FIELD} missing": lambda result: result.pop(RESULT_TYPE_FIELD, None),
        f"{RESULT_TYPE_FIELD} input_required": lambda result: result.update({RESULT_TYPE_FIELD: "input_required"}),
        f"{TTL_FIELD} missing": lambda result: result.pop(TTL_FIELD, None),
        f"{TTL_FIELD} negative": lambda result: result.update({TTL_FIELD: -1}),
        f"{CACHE_SCOPE_FIELD} missing": lambda result: result.pop(CACHE_SCOPE_FIELD, None),
        f"{CACHE_SCOPE_FIELD} unknown": lambda result: result.update({CACHE_SCOPE_FIELD: "shared"}),
        f"{META_FIELD} missing": lambda result: result.pop(META_FIELD, None),
        "serverInfo naming another server": lambda result: result[META_FIELD][META_SERVER_INFO].update({"name": "other"}),
    }
    for label, breaks in broken_copies.items():
        copy = json.loads(json.dumps(raw))
        breaks(copy)
        try:
            check_result_envelope(copy)
        except SnapshotError:
            continue
        failures.append(f"a result with {label} passed the envelope check")
    return failures


def build_arg_parser():
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--url", help="MCP endpoint to send an MCP 2026-07-28 tools/list POST to")
    parser.add_argument(
        "--snapshot",
        type=Path,
        default=DEFAULT_SNAPSHOT,
        help=f"snapshot file to check against (default: {DEFAULT_SNAPSHOT})",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="compare the normalized tools/list against --snapshot instead of printing it; "
        "exits non-zero with a unified diff on drift",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="verify normalize_tools_list() against the checked-in fixture; ignores --url/stdin/--check",
    )
    return parser


def main(argv):
    args = build_arg_parser().parse_args(argv)

    if args.self_test:
        return run_self_test()

    try:
        raw_result = read_raw_result(args)
        check_result_envelope(raw_result)
        normalized_text = to_snapshot_text(normalize_tools_list(raw_result))
    except SnapshotError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    if args.check:
        return run_check(normalized_text, args.snapshot)

    sys.stdout.write(normalized_text)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
