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
       validate-agent-plugin.py --self-test

Also checks analyst preparation restrictions and the session-page default copied from Java, that
every SKILL.md front matter is strict YAML (PyYAML, required), and that no relative reference in a
skill's Markdown leaves its skill directory or points at a file that does not exist. `--self-test`
runs those skill checks over broken skills built in a temporary directory.
"""

import json
import os
import re
import sys
import tempfile
from pathlib import Path

try:
    import yaml
except ImportError:  # reported per skill by skill_frontmatter, never skipped silently
    yaml = None

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
SELF_TEST_FLAG = "--self-test"
FRONTMATTER_DELIMITER = "---"

# What a host resolves against the skill: a backtick-quoted relative file path (a directory and a file
# of a text type, as in `references/guide.md`) or the target of a Markdown link. A command line in
# backticks is not a reference. Same pattern as McpSkillCatalogueTest.RELATIVE_REFERENCE.
RELATIVE_REFERENCE = re.compile(
    r"`((?:\.{1,2}/)*[A-Za-z0-9_-][A-Za-z0-9_.-]*/[A-Za-z0-9_./-]*\.(?:md|txt|json|ya?ml|py|sh))`"
    r"|\]\(([^)\s#]+)\)")
URI_SCHEME = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*:")

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


class StrictLoader(yaml.SafeLoader if yaml else object):
    """PyYAML's safe loader, minus its one leniency: a repeated key replaces the earlier value."""

    def construct_mapping(self, node, deep=False):
        keys = set()
        for key_node, _ in node.value:
            key = self.construct_object(key_node, deep=deep)
            if key in keys:
                raise yaml.constructor.ConstructorError(
                    None, None, f"duplicate key {key!r}", key_node.start_mark)
            keys.add(key)
        return super().construct_mapping(node, deep=deep)


def skill_frontmatter(skill: Path) -> dict | None:
    """The front matter parsed as strict YAML, the way the MCP server's catalogue reads it, or None."""
    lines = skill.read_text().split("\n")
    if lines[0].rstrip() != FRONTMATTER_DELIMITER:
        fail(str(skill), "does not start with YAML front matter")
        return None
    closing = next((i for i, line in enumerate(lines[1:], 1) if line.rstrip() == FRONTMATTER_DELIMITER), None)
    if closing is None:
        fail(str(skill), "front matter has no closing --- line")
        return None
    if yaml is None:
        fail(str(skill), "PyYAML is required to parse the front matter — pip install pyyaml")
        return None
    try:
        document = yaml.load("\n".join(lines[1:closing]), Loader=StrictLoader)
    except yaml.YAMLError as e:
        fail(str(skill), f"front matter is not valid YAML — {' '.join(str(e).split())}")
        return None
    if not isinstance(document, dict):
        fail(str(skill), "front matter must be a YAML mapping")
        return None
    return document


def check_skills(plugin_root: Path) -> None:
    """Section 7.1: each immediate child of skills/ holding a SKILL.md is one skill."""
    skills = sorted(plugin_root.glob("skills/*/SKILL.md"))
    if not skills:
        fail(str(plugin_root / "skills"), "no skills discovered")
    for skill in skills:
        frontmatter = skill_frontmatter(skill)
        if frontmatter is None:
            continue
        name = frontmatter.get("name")
        description = frontmatter.get("description")
        if not isinstance(name, str) or not isinstance(description, str):
            fail(str(skill), "front matter must carry both name and description as text")
            continue
        if name.strip() != skill.parent.name:
            fail(str(skill), f"name {name.strip()!r} must match its directory")
        if len(description.strip()) > 1024:
            fail(str(skill), "description exceeds the 1024-character limit")


def check_skill_links(plugin_root: Path) -> None:
    """A host resolves a relative reference against the skill's own directory and reads only files
    inside it, so no reference in a skill's Markdown may leave that directory, and each must exist."""
    for skill in sorted(plugin_root.glob("skills/*/SKILL.md")):
        skill_dir = skill.parent.resolve()
        for document in sorted(skill.parent.rglob("*.md")):
            for match in RELATIVE_REFERENCE.finditer(document.read_text()):
                reference = match.group(1) or match.group(2)
                if URI_SCHEME.match(reference) or reference.startswith("/"):
                    continue
                target = Path(os.path.normpath(document.parent.resolve() / reference))
                if not target.is_relative_to(skill_dir):
                    fail(str(document), f"{reference!r} leaves its skill directory — "
                                        "spell the content out in the skill instead")
                elif not target.is_file():
                    fail(str(document), f"{reference!r} does not exist")


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


GOOD_SKILL = """---
name: {name}
description: "A skill that loads: \\"quoted\\" words and a colon, all inside one quoted value."
---

# {name}

See `references/guide.md` and [the guide](references/guide.md#top); `mvn -q test` is a command.
"""

# Each case: the files of one broken skill beside a good one, and what the failure must say.
SELF_TEST_CASES = {
    "an unquoted colon in a value": (
        {"bad/SKILL.md": "---\nname: bad\ndescription: the machine underneath: garbage collection\n---\nBody.\n"},
        "front matter is not valid YAML",
    ),
    "a duplicate key": (
        {"bad/SKILL.md": "---\nname: bad\ndescription: one\ndescription: two\n---\nBody.\n"},
        "front matter is not valid YAML",
    ),
    "front matter that is not a mapping": (
        {"bad/SKILL.md": "---\n- name: bad\n---\nBody.\n"},
        "front matter must be a YAML mapping",
    ),
    "an unterminated front matter": (
        {"bad/SKILL.md": "---\nname: bad\ndescription: d\n"},
        "front matter has no closing --- line",
    ),
    "a backtick path into another skill": (
        {"bad/SKILL.md": "---\nname: bad\ndescription: d\n---\nSee `../good/references/guide.md`.\n"},
        "leaves its skill directory",
    ),
    "a Markdown link into another skill": (
        {"bad/SKILL.md": "---\nname: bad\ndescription: d\n---\nSee [it](../good/SKILL.md).\n"},
        "leaves its skill directory",
    ),
    "a link out of a supporting file": (
        {"bad/SKILL.md": "---\nname: bad\ndescription: d\n---\nBody.\n",
         "bad/references/notes.md": "See `../../good/SKILL.md`.\n"},
        "leaves its skill directory",
    ),
    "a link to a file that does not exist": (
        {"bad/SKILL.md": "---\nname: bad\ndescription: d\n---\nSee `references/missing.md`.\n"},
        "does not exist",
    ),
}


def write_skills(plugin_root: Path, files: dict[str, str]) -> None:
    for relative, text in files.items():
        path = plugin_root / "skills" / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)


def skill_problems(plugin_root: Path) -> list[str]:
    failures.clear()
    check_skills(plugin_root)
    check_skill_links(plugin_root)
    found = list(failures)
    failures.clear()
    return found


def located_in(problem: str, skill: str) -> bool:
    """Whether a failure is reported against a file of that skill, rather than merely naming it."""
    return f"/skills/{skill}/" in problem.split(": ", 1)[0]


def self_test() -> int:
    """Runs the skill checks over small plugins built in a temporary directory, one broken skill each."""
    problems = []
    good = {"good/SKILL.md": GOOD_SKILL.format(name="good"), "good/references/guide.md": "# Guide\n"}
    with tempfile.TemporaryDirectory() as directory:
        plugin_root = Path(directory) / "clean"
        write_skills(plugin_root, good)
        found = skill_problems(plugin_root)
        if found:
            problems.append(f"the good skill alone must pass, got {found}")
        for case, (files, expected) in SELF_TEST_CASES.items():
            plugin_root = Path(directory) / re.sub(r"\W+", "-", case)
            write_skills(plugin_root, {**good, **files})
            found = skill_problems(plugin_root)
            if not any(expected in problem and located_in(problem, "bad") for problem in found):
                problems.append(f"{case}: expected a failure saying {expected!r} on the bad skill, got {found}")
            if any(located_in(problem, "good") for problem in found):
                problems.append(f"{case}: the good skill beside it must pass, got {found}")
    for problem in problems:
        print(f"FAIL: self-test — {problem}", file=sys.stderr)
    if problems:
        return 1
    print(f"OK: self-test — a good skill passes and {len(SELF_TEST_CASES)} broken ones are each caught.")
    return 0


def main() -> int:
    if len(sys.argv) > 1 and sys.argv[1] == SELF_TEST_FLAG:
        return self_test()
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
    check_skill_links(plugin_root)
    check_versions(root)
    check_agent_contracts(root)

    if failures:
        for failure in failures:
            print(f"::error::{failure}")
        print(f"\n{len(failures)} problem(s) found.")
        return 1
    print("Agent Plugins manifest, MCP configuration, skills, skill links, versions and agent contracts all valid.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
