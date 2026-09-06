/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.profile.ai.claudecode;

import java.nio.file.Path;
import java.util.List;

/**
 * A single headless invocation of the Claude Code CLI.
 *
 * @param prompt        the user prompt sent on stdin (may embed a transcript of prior turns)
 * @param systemPrompt  appended to the default system prompt via {@code --append-system-prompt-file}
 *                      (may be null/blank to skip)
 * @param model         the model id passed via {@code --model} (may be null/blank to use the CLI default)
 * @param mcpConfigJson the JSON content of an {@code --mcp-config} file (may be null when no tools
 *                      are exposed)
 * @param allowedTools  the fully-qualified tool identifiers the model may call without prompting
 *                      (e.g. {@code mcp__jeffrey_jfr__execute_query}); empty when no tools are exposed
 * @param sourceRoot    the directory the CLI runs in and may read, or null to give it no source
 *                      access at all. It is the working directory rather than an extra readable path
 *                      because the CLI can always read where it was started: leaving it at Jeffrey's
 *                      own working directory and merely adding another would widen the reach instead
 *                      of aiming it
 */
public record ClaudeCodeRequest(
        String prompt,
        String systemPrompt,
        String model,
        String mcpConfigJson,
        List<String> allowedTools,
        Path sourceRoot
) {
    public ClaudeCodeRequest {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }

    public static ClaudeCodeRequest promptOnly(String prompt, String systemPrompt, String model) {
        return new ClaudeCodeRequest(prompt, systemPrompt, model, null, List.of(), null);
    }

    public boolean hasSourceRoot() {
        return sourceRoot != null;
    }

    public boolean hasMcpConfig() {
        return mcpConfigJson != null && !mcpConfigJson.isBlank();
    }
}
