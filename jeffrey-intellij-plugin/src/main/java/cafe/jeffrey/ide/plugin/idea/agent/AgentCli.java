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

package cafe.jeffrey.ide.plugin.idea.agent;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;

import java.io.File;
import java.util.List;

/**
 * A coding agent the panel can hand a profile to.
 *
 * <p>A list rather than two branches, because Codex support costs a row here and the next agent CLI
 * will cost the same. Both are already first-class targets of the {@code microscope} plugin — the
 * repository ships a Claude Code manifest and an Agent Plugins one over the same skills — so leaving
 * either out of the panel would be the inconsistent choice.
 *
 * @param displayName what the button says
 * @param executable  the command looked up on {@code PATH}
 */
public record AgentCli(String displayName, String executable) {

    public static final List<AgentCli> ALL = List.of(
            new AgentCli("Claude", "claude"),
            new AgentCli("Codex", "codex"));

    /**
     * A two-letter badge for the menu and the split button.
     *
     * <p>Two rather than one because the first letter collides immediately — Claude and Codex both
     * start with a C — and derived rather than declared so a new entry in {@link #ALL} needs no
     * second thought. Vendor logos would read better and are not ours to ship.
     */
    public String mark() {
        if (displayName.length() < 2) {
            return displayName.toUpperCase();
        }
        return Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1, 2).toLowerCase();
    }

    /** Where the executable lives, or null when it is not on {@code PATH}. */
    public File find() {
        return PathEnvironmentVariableUtil.findInPath(executable);
    }

    public boolean isInstalled() {
        return find() != null;
    }

    /**
     * The command line, with the task's sentence quoted as one argument. Only profile ids ever reach
     * the quoting, but it is done properly anyway — a command assembled by string concatenation and
     * run in the developer's shell is not the place to assume well-formed input.
     */
    public String command(AgentTask task) {
        return executable + " " + quote(task.prompt());
    }

    private static String quote(String argument) {
        return "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
