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

import java.util.List;

/**
 * Outcome of a headless Claude Code invocation, parsed from the {@code stream-json} output.
 *
 * @param text      the assistant's final text (the {@code result} event), or an error message
 * @param error     true when the CLI reported a failure (non-zero exit or {@code is_error})
 * @param toolsUsed the names of tools the model invoked during the turn (never null)
 */
public record ClaudeCodeResult(
        String text,
        boolean error,
        List<String> toolsUsed
) {
    public ClaudeCodeResult {
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
    }

    public static ClaudeCodeResult success(String text, List<String> toolsUsed) {
        return new ClaudeCodeResult(text, false, toolsUsed);
    }

    public static ClaudeCodeResult failure(String message) {
        return new ClaudeCodeResult(message, true, List.of());
    }
}
