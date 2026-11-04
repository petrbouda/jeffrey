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
"""Where the microscope plugin lives in the repository, named once for every script that reads it.

The directory is the one package every client installs — Claude Code, Codex, Gemini CLI and the
Agent Plugins clients — and both marketplaces point at it. A client keys an install by the plugin's
name (`microscope@jeffrey`), never by this path, so moving the directory means changing this constant,
the marketplace `source` the renderer writes from it, and the few places outside Python that name it.
"""

PLUGIN_DIR = "jeffrey-agent-plugin"
