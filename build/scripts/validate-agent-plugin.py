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
"""Validates the portable half of the microscope plugin.

`claude plugin validate` covers `.claude-plugin/`. Nothing covers the Agent Plugins
manifest that Codex, Cursor, Copilot, VS Code and Kiro read, and the repository is its
own marketplace with no release step in between — a malformed `plugin.json` on master
breaks every install off the default branch the moment it lands.

The checks are self-contained rather than a fetch of the published JSON Schemas: the
spec's own rule is that a client must not retrieve a schema while loading a plugin, and
a CI job that goes red because agent-plugins.org is unreachable is a worse gate than no
gate. The rules encoded below are the ones from Agent Plugins 1.0.0 that actually break
an install, plus the version agreement across the four manifests that only exists in
this repository.

Usage: validate-agent-plugin.py <repo-root>

Also checks analyst preparation restrictions and the session-page default copied from Java.
"""

import json
import re
import sys
from pathlib import Path

SPEC_VERSION = "1.0.0"
PLUGIN_SCHEMA_ID = f"https://agent-plugins.org/schemas/{SPEC_VERSION}/plugin.schema.json"
MCP_SCHEMA_ID = f"https://agent-plugins.org/schemas/{SPEC_VERSION}/mcp.schema.json"

# Section 5.2: the manifest schema is closed at these ten fields.
MANIFEST_FIELDS = {
    "$schema", "name", "version", "description", "author",
    "homepage", "repository", "license", "keywords", "extensions",
}
MANIFEST_REQUIRED = {"$schema", "name"}
NAME_PATTERN = re.compile(r"^(?!.*(?:--|\.\.))[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$")

# Section 7.2.1: one closed variant per transport. Only the remote one is used here.
REMOTE_TRANSPORTS = {"streamable-http", "sse"}
REMOTE_FIELDS = {"type", "url", "headers"}
LOOPBACK_HOSTS = {"localhost", "127.0.0.1", "[::1]"}

PLUGIN_DIR = "jeffrey-claude-plugin"

failures: list[str] = []


def fail(where: str, message: str) -> None:
    failures.append(f"{where}: {message}")


def load(path: Path) -> dict | None:
    if not path.exists():
        fail(str(path), "missing")
        return None
    try:
        return json.loads(path.read_text())
    except json.JSONDecodeError as e:
        fail(str(path), f"invalid JSON — {e}")
        return None


def check_manifest(manifest: dict, where: str) -> None:
    unknown = set(manifest) - MANIFEST_FIELDS
    if unknown:
        fail(where, f"fields outside the closed schema: {sorted(unknown)}")
    missing = MANIFEST_REQUIRED - set(manifest)
    if missing:
        fail(where, f"required fields missing: {sorted(missing)}")
    if manifest.get("$schema") != PLUGIN_SCHEMA_ID:
        fail(where, f"$schema must be exactly {PLUGIN_SCHEMA_ID}")
    name = manifest.get("name", "")
    if not NAME_PATTERN.fullmatch(name) or len(name) > 64:
        fail(where, f"name {name!r} does not match the specification's pattern")


def check_mcp(config: dict, where: str) -> None:
    unknown = set(config) - {"$schema", "mcpServers"}
    if unknown:
        fail(where, f"fields outside the closed schema: {sorted(unknown)}")
    if config.get("$schema") != MCP_SCHEMA_ID:
        fail(where, f"$schema must be exactly {MCP_SCHEMA_ID}")
    servers = config.get("mcpServers")
    if not isinstance(servers, dict):
        fail(where, "mcpServers must be an object")
        return
    for name, server in servers.items():
        at = f"{where} [{name}]"
        transport = server.get("type")
        if transport not in REMOTE_TRANSPORTS:
            fail(at, f"unexpected transport {transport!r} — this plugin serves over HTTP")
            continue
        unknown = set(server) - REMOTE_FIELDS
        if unknown:
            fail(at, f"fields belonging to another variant: {sorted(unknown)}")
        url = server.get("url", "")
        if not url.startswith(("http://", "https://")):
            fail(at, "url must be an absolute HTTP or HTTPS URL")
        elif url.startswith("http://"):
            host = url.removeprefix("http://").split("/", 1)[0].split(":", 1)[0]
            if host not in LOOPBACK_HOSTS:
                fail(at, f"plain HTTP is only allowed on loopback, not {host!r}")


def check_skills(plugin_root: Path) -> None:
    """Section 7.1: each immediate child of skills/ holding a SKILL.md is one skill."""
    skills = sorted(plugin_root.glob("skills/*/SKILL.md"))
    if not skills:
        fail(str(plugin_root / "skills"), "no skills discovered")
    for skill in skills:
        text = skill.read_text()
        if not text.startswith("---\n"):
            fail(str(skill), "does not start with YAML front matter")
            continue
        frontmatter = text.split("---", 2)[1]
        name = re.search(r"^name:\s*(.+)$", frontmatter, re.M)
        description = re.search(r"^description:\s*(.+)$", frontmatter, re.M)
        if not name or not description:
            fail(str(skill), "front matter must carry both name and description")
            continue
        if name.group(1).strip() != skill.parent.name:
            fail(str(skill), f"name {name.group(1).strip()!r} must match its directory")
        if len(description.group(1).strip()) > 1024:
            fail(str(skill), "description exceeds the 1024-character limit")


def check_versions(root: Path) -> None:
    """The four manifests and two marketplaces have to agree, or an update lies."""
    sources = {
        f"{PLUGIN_DIR}/plugin.json": lambda d: d.get("version"),
        f"{PLUGIN_DIR}/.claude-plugin/plugin.json": lambda d: d.get("version"),
        f"{PLUGIN_DIR}/.codex-plugin/plugin.json": lambda d: d.get("version"),
        ".claude-plugin/marketplace.json": lambda d: d["plugins"][0].get("version"),
        ".agents/plugins/marketplace.json": lambda d: d["plugins"][0].get("version"),
    }
    versions = {}
    for path, extract in sources.items():
        document = load(root / path)
        if document is not None:
            versions[path] = extract(document)
    if len(set(versions.values())) > 1:
        fail("versions", f"the manifests disagree: {versions}")


def check_agent_contracts(root: Path) -> None:
    """Cross-check permissions and copied defaults against their source of truth."""
    plugin = root / PLUGIN_DIR
    analyst = plugin / "agents/profile-analyst.md"
    frontmatter = analyst.read_text().split("---", 2)[1]
    denied_section = re.search(r"^disallowedTools:\n((?:  - .+\n)+)", frontmatter, re.M)
    denied = set(re.findall(r"^  - (.+)$", denied_section.group(1), re.M)) if denied_section else set()
    for prefix in ("mcp__plugin_microscope_jeffrey__", "mcp__jeffrey__"):
        if prefix + "heap_prepare" not in denied:
            fail(str(analyst), f"read-only analyst must deny {prefix}heap_prepare")

    for relative in ("codex/agents/profile-analyst.toml", "gemini/agents/profile-analyst.md"):
        path = plugin / relative
        rules = path.read_text().split("## What you never do", 1)[-1]
        if "Never call `heap_prepare`" not in rules:
            fail(str(path), "read-only analyst must explicitly forbid heap_prepare")

    hub_source = root / "jeffrey-microscope/core-microscope/src/main/java/cafe/jeffrey/microscope/core/mcp/tools/HubsMcpTools.java"
    default = re.search(r"DEFAULT_LIMIT\s*=\s*(\d+)", hub_source.read_text())
    skill = plugin / "skills/analyze-hub/SKILL.md"
    documented = re.search(r"hubs_sessions` defaults to (\d+) rows", skill.read_text())
    if not default or not documented or default.group(1) != documented.group(1):
        fail(str(skill), "hubs_sessions documented default must match HubsMcpTools.DEFAULT_LIMIT")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    plugin_root = root / PLUGIN_DIR

    manifest = load(plugin_root / "plugin.json")
    if manifest is not None:
        check_manifest(manifest, f"{PLUGIN_DIR}/plugin.json")

    mcp = load(plugin_root / "mcp.json")
    if mcp is not None:
        check_mcp(mcp, f"{PLUGIN_DIR}/mcp.json")

    codex = load(plugin_root / ".codex-plugin" / "plugin.json")
    if codex is not None and codex.get("mcpServers") != "./mcp.json":
        fail(f"{PLUGIN_DIR}/.codex-plugin/plugin.json",
             "mcpServers must point at ./mcp.json, the one MCP configuration in the package")

    if (plugin_root / ".mcp.json").exists():
        fail(f"{PLUGIN_DIR}/.mcp.json",
             "Claude Code reads this file too, so it would register the server twice — "
             "keep the single mcp.json")

    check_skills(plugin_root)
    check_versions(root)
    check_agent_contracts(root)

    if failures:
        for failure in failures:
            print(f"::error::{failure}")
        print(f"\n{len(failures)} problem(s) found.")
        return 1
    print("Agent Plugins manifest, MCP configuration, skills, versions and agent contracts all valid.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
