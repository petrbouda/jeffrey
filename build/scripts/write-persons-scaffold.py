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
"""Writes the persons service's repository into every eval case that scaffolds it.

A persons case that works on the code (its `case.yaml` names `scaffold_script: setup.sh`) gets a `setup.sh`
that writes the sources the two real runs were built from — `evals/fixtures/testapp/`, the server of
petrbouda/jeffrey-testapp — verbatim between one heredoc marker per file, and commits them, so the agent's
working directory holds the code a finding names. test-plugin-eval-mocks.py checks every embed byte for byte.
The fixture carries no build file (pom.xml): the plugin's SessionStart hook checks for a running Jeffrey in a
JVM build, and the mocked suite runs none — the checkout scaffold has none either.

    python3 build/scripts/write-persons-scaffold.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "build/scripts"))
from plugin_layout import PLUGIN_DIR  # noqa: E402

PLUGIN = ROOT / PLUGIN_DIR
SOURCES = PLUGIN / "evals/fixtures/testapp"
CASES = PLUGIN / "evals/cases"
WORKLOAD_TAG = "persons"
SCAFFOLD = "scaffold_script: setup.sh"
TAGS = re.compile(r"^tags:\s*\[(.*)\]$", re.M)
NOT_A_MARKER = re.compile(r"[^A-Z0-9]+")
HEADER = """#!/usr/bin/env bash
# The persons service's repository: the server of jeffrey-testapp the two real runs ("persons direct",
# "persons dom") were built from, committed, so the working directory holds the code a finding names. The files
# between the markers are fixtures/testapp/ verbatim, written by build/scripts/write-persons-scaffold.py
# (test-plugin-eval-mocks.py checks them).
set -euo pipefail
"""
FOOTER = """git init -q
git add -A
git -c user.name=eval -c user.email=eval@example.com commit -q -m "persons service"
"""


def marker(path: Path) -> str:
    """The heredoc marker of a source: its path, upper case, every other character an underscore."""
    return "TESTAPP_" + NOT_A_MARKER.sub("_", str(path).upper()).strip("_")


def sources() -> list[Path]:
    return sorted(path.relative_to(SOURCES) for path in SOURCES.rglob("*") if path.is_file())


def script() -> str:
    parts = [HEADER]
    for directory in sorted({str(path.parent) for path in sources() if str(path.parent) != "."}):
        parts.append(f"mkdir -p {directory}\n")
    for path in sources():
        text = (SOURCES / path).read_text()
        if not text.endswith("\n"):
            text += "\n"
        name = marker(path)
        parts.append(f"cat > {path} <<'{name}'\n{text}{name}\n")
    parts.append(FOOTER)
    return "".join(parts)


def scaffolded_cases() -> list[Path]:
    """The persons cases whose case.yaml asks for the scaffold."""
    cases = []
    for case in sorted(CASES.iterdir()):
        prompt, config = case / "prompt.md", case / "case.yaml"
        if not (prompt.is_file() and config.is_file()) or SCAFFOLD not in config.read_text():
            continue
        tags = TAGS.search(prompt.read_text())
        if tags and WORKLOAD_TAG in [tag.strip() for tag in tags.group(1).split(",")]:
            cases.append(case)
    return cases


def main() -> int:
    text = script()
    for case in scaffolded_cases():
        target = case / "setup.sh"
        target.write_text(text)
        target.chmod(0o755)
        print(f"wrote {target.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
