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
"""Checks that everything the microscope plugin names exists on the MCP server.

The skills, the agents and the README tell a model which tool to call, with which arguments, and
which field of the answer to read. None of that is code, so nothing failed when a skill sent the
model to a field the server never returns, or when an agent listed its tools in a form Claude Code
does not resolve. This script holds every such name to `build/mcp/tools-list.snapshot.json` — the
tool list CI already pins against the packaged jar — in every Markdown and TOML file of the plugin:

- a tool name (`family_toolName`, bare or behind a client's prefix) is a tool the server has;
- an argument in a call (`tool(arg=…)`, `tool arg=…`) is one of that tool's inputs, and an
  upper-case value given to an enum input is one of its values;
- `` `tool` with `key: value` `` names an input or an output field of that tool;
- a backticked camelCase name is a field of some tool's input or output, and a backticked
  UPPER_CASE token is a value of some enum — unless it is on the allowlists below, which say why;
- an agent's `tools:`/`disallowedTools:` MCP entry is a real tool or a whole server, never a family
  wildcard such as `mcp__<server>__heap_*`, which Claude Code drops as unrecognized.

`McpToolsetAssemblerTest.ToolReferences.pluginSkillsNameToolsThatExist` checks tool names in the
skill bodies from Java; this covers the rest of the plugin and the parts of a reference that are
not a tool name.

Usage: check-plugin-references.py <repo-root>
       check-plugin-references.py --self-test
"""

import json
import re
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

PLUGIN_DIR = "jeffrey-claude-plugin"
TOOLS_SNAPSHOT = "build/mcp/tools-list.snapshot.json"
SELF_TEST_FLAG = "--self-test"
CHECKED_SUFFIXES = {".md", ".toml"}
FRONTMATTER_DELIMITER = "---"
AGENT_TOOL_LISTS = ("tools", "disallowedTools")
WHOLE_SERVER = "*"

# The names a client puts in front of a tool, longest first so the plugin's own prefix is taken
# whole. Stripped before tool names are matched, so a prefixed name is checked like a bare one.
CLIENT_PREFIXES = ("mcp__plugin_microscope_jeffrey__", "mcp__jeffrey__", "mcp_jeffrey_")

# `family_toolName` in prose — the same pattern as McpToolsetAssemblerTest.ToolReferences.REFERENCE.
TOOL_REFERENCE = re.compile(r"\b([a-z][a-z]*_[a-zA-Z][a-zA-Z0-9]*)\b")

# Deliberate counter-examples ("`jfr_listTables`, not `jfr_list_tables`") and a SQL alias — the same
# set as McpToolsetAssemblerTest.ToolReferences.NOT_REFERENCES.
NOT_TOOL_REFERENCES = {
    "jfr_list_tables", "heap_get_leak_suspects", "compare_movements_list", "remote_list_sessions", "heap_used",
}

ARGUMENT = re.compile(r"([a-zA-Z]+)\s*[=:]\s*(\"[^\"]*\"|\[[^\]]*\]|[^,)\s]+)")
UPPER_LITERAL = re.compile(r"[A-Z][A-Z0-9_]*")
CAMEL_CASE = re.compile(r"[a-z][a-z0-9]*[A-Z][A-Za-z0-9]*")
ENUM_LIKE = re.compile(r"[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+|[A-Z]{3,}")
# The two backticked shapes that name a field or a value: a bare or dotted name (`followUp.nextTools`,
# `NOT_RECORDED`) and a pair (`status: NOT_RECORDED`, `useWeight=true`). A phrase with spaces — SQL,
# a command line, `GET /orders` — names neither, so it is not read for them.
DOTTED_NAME = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*")
PAIR = re.compile(r"([A-Za-z_][A-Za-z0-9_]*)\s*[:=]\s*([A-Za-z_][A-Za-z0-9_]*)")
BACKTICKED = re.compile(r"`([^`\n]+)`")
FENCED_BLOCK = re.compile(r"^```.*?^```", re.S | re.M)
MCP_TOOL_ENTRY = re.compile(r"^mcp__(?P<server>[A-Za-z0-9_-]+?)__(?P<tool>.+)$")

# camelCase names the plugin quotes that are not fields of any Jeffrey answer, by what they are.
# A new one is added here with the others of its kind; a typo of a real field is the thing to fix.
NAMES_OUTSIDE_THE_SCHEMAS = {
    # JFR event fields, read inside `fields` by SQL (jfr-sql)
    "allocationSize", "codeSize", "compileId", "isOsr", "longestPause", "sumOfPauses", "totalTimeSpent",
    # JDK and application methods the skills name as examples
    "containsKey", "fillInStackTrace", "inflateBytes", "tryParse",
    # client and build configuration keys (Gemini, Claude Code, Maven, Gradle)
    "disallowedTools", "excludeTools", "groupId", "httpUrl", "jvmArgs", "mcpServers",
    # MCP protocol fields of every result, not of one tool's schema
    "outputSchema", "structuredContent",
}

# UPPER_CASE tokens the plugin quotes that are not enum values of any Jeffrey answer, by what they are.
TOKENS_OUTSIDE_THE_SCHEMAS = {
    # SQL and OQL keywords and types
    "AND", "ATTACH", "INSTANCEOF", "LIKE", "LIMIT", "NULL", "SELECT", "UNNEST",
    # environment variables the clients and the hook read
    "CLAUDE_PLUGIN_ROOT", "JAVA_TOOL_OPTIONS", "JEFFREY_MCP_ENDPOINT", "JEFFREY_MCP_TOKEN",
    "MCP_SDK_GENERATION",
    # git and the operating system
    "HEAD", "SIGSEGV",
}


@dataclass(frozen=True)
class Problem:
    path: Path
    line: int
    message: str

    def __str__(self) -> str:
        return f"{self.path}:{self.line}: {self.message}"


@dataclass(frozen=True)
class ToolSurface:
    """What the server advertises, read from the snapshot of its tools/list."""
    inputs: dict[str, dict]
    outputs: dict[str, set[str]]
    field_names: set[str]
    enum_values: set[str]

    @property
    def names(self) -> set[str]:
        return set(self.inputs)

    @property
    def families(self) -> set[str]:
        return {name.split("_", 1)[0] for name in self.inputs}

    @staticmethod
    def of(snapshot: dict) -> "ToolSurface":
        inputs, outputs, field_names, enum_values = {}, {}, set(), set()
        for tool in snapshot["tools"]:
            schema = tool.get("inputSchema") or {}
            inputs[tool["name"]] = schema.get("properties") or {}
            outputs[tool["name"]] = set()
            for part, sink in ((schema, set()), (tool.get("outputSchema") or {}, outputs[tool["name"]])):
                collect(part, sink, enum_values)
                field_names |= sink
            collect(schema, field_names, enum_values)
        return ToolSurface(inputs, outputs, field_names, enum_values)

    def enum_of(self, tool: str, argument: str) -> list[str] | None:
        schema = self.inputs[tool].get(argument) or {}
        return schema.get("enum") or (schema.get("items") or {}).get("enum")


def collect(schema, names: set[str], enum_values: set[str]) -> None:
    """Every property name and enum value anywhere inside one JSON Schema."""
    if isinstance(schema, list):
        for part in schema:
            collect(part, names, enum_values)
        return
    if not isinstance(schema, dict):
        return
    for name, part in (schema.get("properties") or {}).items():
        names.add(name)
        collect(part, names, enum_values)
    enum_values.update(value for value in schema.get("enum") or [] if isinstance(value, str))
    for key, part in schema.items():
        if key != "properties" and isinstance(part, (dict, list)):
            collect(part, names, enum_values)


def line_of(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def without_fences(text: str) -> str:
    """The text with each fenced block blanked line for line, so offsets keep their line numbers."""
    return FENCED_BLOCK.sub(lambda block: "\n" * block.group(0).count("\n"), text)


def without_prefixes(text: str) -> str:
    for prefix in CLIENT_PREFIXES:
        text = text.replace(prefix, "")
    return text


def tool_name_problems(path: Path, text: str, surface: ToolSurface) -> list[Problem]:
    problems = []
    families, names = surface.families, surface.names
    for match in TOOL_REFERENCE.finditer(without_prefixes(text)):
        reference = match.group(1)
        if reference in names or reference in NOT_TOOL_REFERENCES:
            continue
        if reference.split("_", 1)[0] in families:
            problems.append(Problem(path, line_of(text, match.start()), f"no tool is named {reference}"))
    return problems


def argument_problems(path: Path, text: str, surface: ToolSurface) -> list[Problem]:
    problems = []
    tools = "|".join(sorted(map(re.escape, surface.names), key=len, reverse=True))
    calls = re.compile(rf"\b({tools})\b(\([^)\n]*\)|(?:[ \t]+[a-zA-Z]+=\S+)+)")
    prose = re.compile(rf"`({tools})`[^`\n.;]{{0,40}}?\bwith\s+`([a-zA-Z]+)(?:\s*[:=]\s*([^`]+))?`")
    for match in calls.finditer(text):
        tool = match.group(1)
        for argument, value in ARGUMENT.findall(match.group(2)):
            line = line_of(text, match.start())
            if argument not in surface.inputs[tool]:
                problems.append(Problem(path, line, f"{tool} takes no argument {argument}"))
            else:
                problems += enum_value_problems(path, line, surface, tool, argument, value)
    for match in prose.finditer(text):
        tool, key, value = match.groups()
        line = line_of(text, match.start())
        if key not in surface.inputs[tool] and key not in surface.outputs[tool]:
            problems.append(Problem(path, line, f"{tool} has no input or output field {key}"))
        elif key in surface.inputs[tool] and value:
            problems += enum_value_problems(path, line, surface, tool, key, value)
    return problems


def enum_value_problems(path: Path, line: int, surface: ToolSurface, tool: str, argument: str,
                        value: str) -> list[Problem]:
    allowed = surface.enum_of(tool, argument)
    literal = value.strip("\"' ")
    if allowed and UPPER_LITERAL.fullmatch(literal) and literal not in allowed:
        return [Problem(path, line, f"{tool}.{argument} has no value {literal} (it takes {', '.join(allowed)})")]
    return []


def quoted_name_problems(path: Path, text: str, surface: ToolSurface,
                         allowlisted: set[str] | None = None) -> list[Problem]:
    """Backticked fields and enum values; `allowlisted` collects the allowlist entries that were needed."""
    problems = []
    allowlisted = set() if allowlisted is None else allowlisted
    tool_suffixes = {name.split("_", 1)[1] for name in surface.names}
    prose = without_fences(text)
    for match in BACKTICKED.finditer(prose):
        line = line_of(prose, match.start())
        for word in quoted_words(without_prefixes(match.group(1)).strip()):
            if CAMEL_CASE.fullmatch(word):
                if word in surface.field_names or word in tool_suffixes:
                    continue
                if word in NAMES_OUTSIDE_THE_SCHEMAS:
                    allowlisted.add(word)
                else:
                    problems.append(Problem(path, line, f"no tool's input or output has a field {word} "
                                                        f"(a name outside the schemas goes on "
                                                        f"NAMES_OUTSIDE_THE_SCHEMAS)"))
            elif ENUM_LIKE.fullmatch(word):
                if word in surface.enum_values:
                    continue
                if word in TOKENS_OUTSIDE_THE_SCHEMAS:
                    allowlisted.add(word)
                else:
                    problems.append(Problem(path, line, f"no enum of any tool has a value {word} "
                                                        f"(a token outside the schemas goes on "
                                                        f"TOKENS_OUTSIDE_THE_SCHEMAS)"))
    return problems


def quoted_words(quoted: str) -> list[str]:
    if DOTTED_NAME.fullmatch(quoted):
        return quoted.split(".")
    pair = PAIR.fullmatch(quoted)
    return list(pair.groups()) if pair else []


def agent_entry_problems(path: Path, text: str, surface: ToolSurface) -> list[Problem]:
    """A Claude Code agent's MCP entries: an exact tool or a whole server, nothing between."""
    if not text.startswith(FRONTMATTER_DELIMITER):
        return []
    frontmatter = text.split(FRONTMATTER_DELIMITER, 2)[1]
    problems = []
    for key in AGENT_TOOL_LISTS:
        section = re.search(rf"^{key}:\n((?:  - .+\n)+)", frontmatter, re.M)
        if section is None:
            continue
        for entry in re.finditer(r"^  - (.+)$", section.group(1), re.M):
            match = MCP_TOOL_ENTRY.match(entry.group(1))
            if match is None or match.group("tool") == WHOLE_SERVER:
                continue
            tool = match.group("tool")
            line = line_of(text, text.index(section.group(0)) + entry.start())
            if "*" in tool:
                problems.append(Problem(path, line, f"{key} entry {entry.group(1)} is a family wildcard, which "
                                                    f"Claude Code drops as unrecognized - list the tools by "
                                                    f"name or take the whole server"))
            elif tool not in surface.names:
                problems.append(Problem(path, line, f"{key} entry {entry.group(1)} names no tool"))
    return problems


def check_plugin(plugin_root: Path, surface: ToolSurface, allowlisted: set[str] | None = None) -> list[Problem]:
    problems = []
    for path in sorted(p for p in plugin_root.rglob("*") if p.suffix in CHECKED_SUFFIXES and p.is_file()):
        text = path.read_text()
        problems += tool_name_problems(path, text, surface)
        problems += argument_problems(path, text, surface)
        problems += quoted_name_problems(path, text, surface, allowlisted)
        if path.parent.name == "agents" and path.parent.parent == plugin_root:
            problems += agent_entry_problems(path, text, surface)
    return problems


# --- self-test -----------------------------------------------------------------------------------

SELF_TEST_SNAPSHOT = {"tools": [
    {"name": "heap_prepare",
     "inputSchema": {"properties": {"profileId": {"type": "string"},
                                    "report": {"type": "string", "enum": ["LEAKS", "DOMINATOR"]}}},
     "outputSchema": {"properties": {"status": {"type": "string", "enum": ["NOT_RUN_YET", "RUNNING"]},
                                     "operationId": {"type": "string"}}}},
    {"name": "heap_status",
     "inputSchema": {"properties": {"profileId": {"type": "string"}}},
     "outputSchema": {"properties": {"stageCount": {"type": "integer"}}}},
]}

GOOD_DOCUMENT = """# Good

Call `heap_prepare` with `report: LEAKS`, then `heap_status(profileId=p-1)`; a cached report reads
`status: NOT_RUN_YET` until then, and its `operationId` goes to `mcp__jeffrey__heap_status`.
`jfr_list_tables` is a counter-example, `sumOfPauses` a JFR field and `SELECT` a SQL keyword.

```sql
SELECT unknownField FROM NOT_A_VALUE
```
"""

GOOD_AGENT = """---
name: good
tools:
  - mcp__plugin_microscope_jeffrey__heap_prepare
  - mcp__jeffrey__*
---
Body.
"""

SELF_TEST_CASES = {
    "a tool that does not exist": ("skills/bad/SKILL.md", "Call `heap_rebuild` first.\n", "no tool is named heap_rebuild"),
    "a prefixed tool that does not exist": ("README.md", "Allow `mcp__jeffrey__heap_rebuild`.\n",
                                            "no tool is named heap_rebuild"),
    "an argument the tool does not take": ("skills/bad/SKILL.md", "`heap_prepare(force=true)`\n",
                                           "heap_prepare takes no argument force"),
    "an enum value the argument does not take": ("skills/bad/SKILL.md", "heap_prepare report=STRINGS\n",
                                                 "heap_prepare.report has no value STRINGS"),
    "a with-field the tool does not have": ("skills/bad/SKILL.md", "`heap_status` with `stageTotal: 3`\n",
                                            "heap_status has no input or output field stageTotal"),
    "a field no answer has": ("skills/bad/SKILL.md", "Read `followUp.nextTools`.\n",
                              "no tool's input or output has a field nextTools"),
    "an enum value no answer has": ("skills/bad/SKILL.md", "A `NOT_COMPUTED` answer.\n",
                                    "no enum of any tool has a value NOT_COMPUTED"),
    "a family wildcard in an agent": ("agents/bad.md",
                                      "---\nname: bad\ntools:\n  - mcp__jeffrey__heap_*\n---\nBody.\n",
                                      "is a family wildcard"),
    "an agent entry that names no tool": ("agents/bad.md",
                                          "---\nname: bad\ndisallowedTools:\n  - mcp__jeffrey__heap_rebuild\n---\n",
                                          "names no tool"),
}


def self_test() -> int:
    surface = ToolSurface.of(SELF_TEST_SNAPSHOT)
    failures = []
    with tempfile.TemporaryDirectory() as directory:
        good_root = Path(directory) / "good"
        write(good_root, {"skills/good/SKILL.md": GOOD_DOCUMENT, "agents/good.md": GOOD_AGENT})
        found = check_plugin(good_root, surface)
        if found:
            failures.append(f"the good documents must pass, got {[str(p) for p in found]}")
        for case, (relative, text, expected) in SELF_TEST_CASES.items():
            root = Path(directory) / re.sub(r"\W+", "-", case)
            write(root, {relative: text})
            found = check_plugin(root, surface)
            if not any(expected in problem.message for problem in found):
                failures.append(f"{case}: expected a problem saying {expected!r}, got {[str(p) for p in found]}")
    for failure in failures:
        print(f"FAIL: self-test — {failure}", file=sys.stderr)
    if failures:
        return 1
    print(f"OK: self-test — good documents pass and {len(SELF_TEST_CASES)} broken references are each caught.")
    return 0


def write(root: Path, files: dict[str, str]) -> None:
    for relative, text in files.items():
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)


def main() -> int:
    if len(sys.argv) > 1 and sys.argv[1] == SELF_TEST_FLAG:
        return self_test()
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    surface = ToolSurface.of(json.loads((root / TOOLS_SNAPSHOT).read_text()))
    allowlisted: set[str] = set()
    problems = check_plugin(root / PLUGIN_DIR, surface, allowlisted)
    for problem in problems:
        print(f"::error::{problem}")
    # An allowlist entry no file needs any more would excuse the next typo of the same spelling.
    stale = (NAMES_OUTSIDE_THE_SCHEMAS | TOKENS_OUTSIDE_THE_SCHEMAS) - allowlisted
    for entry in sorted(stale):
        print(f"::error::{Path(__file__).name}: {entry} is allowlisted but no plugin file quotes it - remove it")
    if problems or stale:
        print(f"\n{len(problems)} reference(s) the server does not have, {len(stale)} stale allowlist entries.")
        return 1
    print(f"Every tool, argument, field and enum value the plugin names exists on the server "
          f"({len(surface.names)} tools).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
