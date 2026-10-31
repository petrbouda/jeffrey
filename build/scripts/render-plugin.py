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
"""Renders the parts of the microscope plugin that are written once and shipped many times.

The plugin repeats itself by design: a skill is self-contained, so the rules for linking into
Microscope and the sentence about tool-name prefixes are in every skill that needs them, and each
agent ships three times — as a Claude Code subagent, a Codex custom agent and a Gemini CLI subagent —
because no format carries the others. Kept by hand, the copies drifted: a sentence lost its middle in
one dialect, a rule reached two of three, and a tool list used a wildcard Claude Code does not resolve.

So those parts have one source each under `build/plugin/`, and this script writes the copies:

- `shared/*.md` — a section or a sentence, written into every skill listed for it;
- `agents/<name>.md` — one agent: its description once, each dialect's settings, and one body — with
  `<!-- include: shared/<fragment>.md -->` lines for text two agents share — whose
  `<!-- claude -->` / `<!-- portable -->` / `<!-- end -->` blocks hold what only one dialect says
  (the portable text is Codex's and Gemini's, which are the same). A Claude Code tool list names
  families (`heap_*`) or the whole server (`*`); it is written out as exact tool names from
  `build/mcp/tools-list.snapshot.json` behind both prefixes, so a new tool in a family reaches the
  agent and no family wildcard ever reaches Claude Code.

Usage: render-plugin.py <repo-root>            writes every generated file
       render-plugin.py <repo-root> --check    fails, naming them, when any is not what it would write
"""

import json
import re
import sys
import textwrap
from dataclasses import dataclass
from pathlib import Path

import yaml

PLUGIN_DIR = "jeffrey-claude-plugin"
SOURCE_DIR = "build/plugin"
TOOLS_SNAPSHOT = "build/mcp/tools-list.snapshot.json"
CHECK_FLAG = "--check"

# The two names the plugin's server goes by in Claude Code: its own, and a server registered by hand.
CLAUDE_PREFIXES = ("mcp__plugin_microscope_jeffrey__", "mcp__jeffrey__")
WHOLE_SERVER = "*"
FAMILY_WILDCARD = re.compile(r"^(?P<family>[a-z]+)_\*$")

# A line of its own that puts a shared fragment into an agent's body, so two agents can carry one text.
INCLUDE = re.compile(r"^<!-- include: (?P<source>shared/[\w.-]+\.md) -->$")
CLAUDE_BLOCK, PORTABLE_BLOCK, END_BLOCK = "<!-- claude -->", "<!-- portable -->", "<!-- end -->"
DIALECT_BLOCKS = {CLAUDE_BLOCK: "claude", PORTABLE_BLOCK: "portable"}
TOML_WIDTH = 103


@dataclass(frozen=True)
class SharedSection:
    """A `## Heading` section, replaced whole — heading to the next `## ` — in each skill listed."""
    source: str
    heading: str
    skills: tuple[str, ...]


@dataclass(frozen=True)
class SharedSentence:
    """A sentence replaced wherever `pattern` matches it, in each skill listed."""
    source: str
    pattern: re.Pattern
    skills: tuple[str, ...]


SHARED_SECTIONS = (
    SharedSection("shared/link-what-you-name.md", "## Link what you name", (
        "advise-jfr", "analyze-heap", "analyze-jfr", "analyze-remote", "compare-jfr", "heap-sql", "help",
        "jfr-sql", "profile-run", "regression-check")),
)

SHARED_SENTENCES = (
    SharedSentence("shared/tool-prefix.md",
                   re.compile(r"Tool names (?:below|here) omit the prefix your client puts in front of them — "
                              r".*?the rest of the name is exact camelCase"), (
        "advise-jfr", "analyze-exceptions", "analyze-heap", "analyze-jfr", "analyze-remote", "compare-jfr",
        "heap-sql", "help", "jfr-sql", "profile-run", "regression-check")),
)


class RenderError(Exception):
    pass


# --- skills --------------------------------------------------------------------------------------

def render_skill(text: str, skill: str, sources: Path) -> str:
    for section in SHARED_SECTIONS:
        if skill in section.skills:
            text = with_section(text, section, (sources / section.source).read_text(), skill)
    for sentence in SHARED_SENTENCES:
        if skill in sentence.skills:
            replacement = (sources / sentence.source).read_text().strip()
            text, count = sentence.pattern.subn(lambda _: replacement, text)
            if count != 1:
                raise RenderError(f"skills/{skill}: expected the sentence from {sentence.source} once, found {count}")
    return text


def with_section(text: str, section: SharedSection, fragment: str, skill: str) -> str:
    start = text.find(section.heading + "\n")
    if start < 0:
        raise RenderError(f"skills/{skill}: has no '{section.heading}' section for {section.source}")
    following = re.search(r"^## ", text[start + len(section.heading):], re.M)
    end = start + len(section.heading) + following.start() if following else len(text)
    tail = "\n" if following else ""
    return text[:start] + fragment.rstrip("\n") + "\n" + tail + text[end:]


# --- agents --------------------------------------------------------------------------------------

@dataclass(frozen=True)
class AgentSource:
    name: str
    description: str
    claude: dict
    codex: dict
    gemini: dict | None
    body: str

    @staticmethod
    def read(path: Path, sources: Path) -> "AgentSource":
        _, front, body = path.read_text().split("---\n", 2)
        data = yaml.safe_load(front)
        return AgentSource(data["name"], data["description"], data["claude"], data["codex"], data.get("gemini"),
                           included(body.strip("\n"), sources, data["name"]))

    def body_for(self, dialect: str) -> str:
        """The body with the other dialect's blocks dropped and every block marker removed."""
        lines, current = [], None
        for number, line in enumerate(self.body.splitlines(), 1):
            if line in DIALECT_BLOCKS:
                current = DIALECT_BLOCKS[line]
            elif line == END_BLOCK:
                if current is None:
                    raise RenderError(f"agents/{self.name}: {END_BLOCK} without a block at body line {number}")
                current = None
            elif current is None or current == dialect:
                lines.append(line)
        if current is not None:
            raise RenderError(f"agents/{self.name}: a {current} block is never closed")
        return "\n".join(lines)


def included(body: str, sources: Path, agent: str) -> str:
    """The body with each include line replaced by the shared fragment it names."""
    lines = []
    for line in body.splitlines():
        include = INCLUDE.match(line)
        if include is None:
            lines.append(line)
            continue
        fragment = sources / include.group("source")
        if not fragment.exists():
            raise RenderError(f"agents/{agent}: includes {include.group('source')}, which does not exist")
        lines += fragment.read_text().rstrip("\n").splitlines()
    return "\n".join(lines)


def claude_entries(specs: list[str], tools: set[str], agent: str) -> list[str]:
    """Each family or exact tool behind both prefixes, in the order given; other tools as they are."""
    mcp, other = [], []
    for spec in specs:
        family = FAMILY_WILDCARD.match(spec)
        if spec == WHOLE_SERVER:
            mcp.append([WHOLE_SERVER])
        elif family:
            members = sorted(tool for tool in tools if tool.startswith(family.group("family") + "_"))
            if not members:
                raise RenderError(f"agents/{agent}: no tool in the {spec} family")
            mcp.append(members)
        elif "_" in spec:
            if spec not in tools:
                raise RenderError(f"agents/{agent}: no tool is named {spec}")
            mcp.append([spec])
        else:
            other.append(spec)
    return [prefix + tool for prefix in CLAUDE_PREFIXES for group in mcp for tool in group] + other


def render_claude(agent: AgentSource, tools: set[str]) -> str:
    settings = agent.claude
    lines = ["---", f"name: {agent.name}", f"description: {agent.description}"]
    for key in ("tools", "disallowedTools"):
        if settings.get(key):
            lines.append(f"{key}:")
            lines += [f"  - {entry}" for entry in claude_entries(settings[key], tools, agent.name)]
    lines.append(f"model: {settings['model']}")
    if settings.get("skills"):
        lines.append("skills:")
        lines += [f"  - {skill}" for skill in settings["skills"]]
    lines += [f"color: {settings['color']}", "---", "", agent.body_for("claude")]
    return "\n".join(lines) + "\n"


def comment(header: str) -> list[str]:
    return [f"# {line}" if line else "#" for line in header.rstrip("\n").splitlines()]


def render_codex(agent: AgentSource) -> str:
    body = agent.body_for("portable")
    for text in (agent.description, body):
        if '"""' in text or "\\" in text:
            raise RenderError(f"agents/{agent.name}: a TOML basic string cannot hold triple quotes or backslashes")
    wrapped = textwrap.wrap(agent.description, TOML_WIDTH - 2, break_long_words=False, break_on_hyphens=False)
    description = " \\\n".join(wrapped)
    lines = comment(agent.codex["header"]) + [
        "", f'name = "{agent.name}"', "", 'description = """', description, '"""', "",
        f'sandbox_mode = "{agent.codex["sandbox_mode"]}"', "", 'developer_instructions = """', body, '"""']
    return "\n".join(lines) + "\n"


def render_gemini(agent: AgentSource) -> str:
    settings = agent.gemini
    lines = ["---"] + comment(settings["header"]) + [f"name: {agent.name}", f"description: {agent.description}",
                                                    "tools:"]
    lines += [f"  - {tool}" for tool in settings["tools"]]
    lines += [f"timeout_mins: {settings['timeout_mins']}", "---", "", agent.body_for("portable")]
    return "\n".join(lines) + "\n"


# --- the whole plugin ----------------------------------------------------------------------------

def rendered(root: Path) -> dict[Path, str]:
    """Every generated file of the plugin, by path, as this script would write it."""
    sources = root / SOURCE_DIR
    plugin = root / PLUGIN_DIR
    tools = {tool["name"] for tool in json.loads((root / TOOLS_SNAPSHOT).read_text())["tools"]}
    files: dict[Path, str] = {}
    skills = {skill for group in SHARED_SECTIONS + SHARED_SENTENCES for skill in group.skills}
    for skill in sorted(skills):
        path = plugin / "skills" / skill / "SKILL.md"
        files[path] = render_skill(path.read_text(), skill, sources)
    for source in sorted((sources / "agents").glob("*.md")):
        agent = AgentSource.read(source, sources)
        files[plugin / "agents" / f"{agent.name}.md"] = render_claude(agent, tools)
        files[plugin / "codex" / "agents" / f"{agent.name}.toml"] = render_codex(agent)
        if agent.gemini is not None:
            files[plugin / "gemini" / "agents" / f"{agent.name}.md"] = render_gemini(agent)
    return files


def orphaned(root: Path, files: dict[Path, str]) -> list[Path]:
    """Agent files in a rendered directory that no source renders — what a deleted agent leaves behind."""
    plugin = root / PLUGIN_DIR
    rendered_dirs = ((plugin / "agents", "*.md"), (plugin / "codex" / "agents", "*.toml"),
                     (plugin / "gemini" / "agents", "*.md"))
    return sorted(path for directory, pattern in rendered_dirs for path in directory.glob(pattern) if path not in files)


def main() -> int:
    arguments = [argument for argument in sys.argv[1:] if argument != CHECK_FLAG]
    check = CHECK_FLAG in sys.argv[1:]
    root = Path(arguments[0] if arguments else ".").resolve()
    try:
        files = rendered(root)
    except RenderError as error:
        print(f"::error::{error}")
        return 1
    stale = [path for path, text in files.items() if not path.exists() or path.read_text() != text]
    orphans = orphaned(root, files)
    if check:
        for path in stale:
            print(f"::error::{path.relative_to(root)} is not what {SOURCE_DIR}/ renders - "
                  f"edit the source and run build/scripts/render-plugin.py")
        for path in orphans:
            print(f"::error::{path.relative_to(root)} has no source in {SOURCE_DIR}/agents/ - "
                  f"delete it, or add the agent's source")
        if stale or orphans:
            return 1
        print(f"All {len(files)} generated plugin files match their sources in {SOURCE_DIR}/.")
        return 0
    for path in stale:
        path.write_text(files[path])
        print(f"wrote {path.relative_to(root)}")
    for path in orphans:
        path.unlink()
        print(f"removed {path.relative_to(root)}, which no source renders")
    print(f"{len(stale)} of {len(files)} generated plugin files rewritten.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
