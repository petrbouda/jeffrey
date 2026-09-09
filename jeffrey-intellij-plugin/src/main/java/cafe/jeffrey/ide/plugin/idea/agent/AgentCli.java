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
 * will cost the same. All three are already first-class targets of the {@code microscope} plugin —
 * the repository ships a Claude Code manifest, an Agent Plugins one and a Gemini CLI extension over
 * the same skills — so leaving any of them out of the panel would be the inconsistent choice.
 *
 * @param displayName what the button says
 * @param executable  the command looked up on {@code PATH}
 * @param promptStyle how that command takes the opening sentence
 */
public record AgentCli(String displayName, String executable, PromptStyle promptStyle) {

    public static final List<AgentCli> ALL = List.of(
            new AgentCli("Claude", "claude", PromptStyle.POSITIONAL),
            new AgentCli("Codex", "codex", PromptStyle.POSITIONAL),
            new AgentCli("Gemini", "gemini", PromptStyle.INTERACTIVE_OPTION));

    /**
     * How an agent takes an opening prompt on its command line.
     *
     * <p>The panel wants one thing from every agent: a session that starts with the profile in hand
     * and stays open for the next question. Claude Code and Codex give that for a prompt written as
     * a positional argument. Gemini reads the same argument as a batch run — it answers once and
     * exits — and keeps the session only behind {@code -i}, so spelling its command like the other
     * two would replace the conversation the panel exists to open with a paragraph in a dead
     * terminal.
     *
     * <p>A style rather than a raw flag string, because the difference being encoded is what happens
     * to the session, not which characters go in front of the sentence.
     */
    public enum PromptStyle {

        /** The prompt is a positional argument and the session stays open. */
        POSITIONAL(""),

        /** The prompt needs an option to stay interactive. */
        INTERACTIVE_OPTION("-i");

        private final String option;

        PromptStyle(String option) {
            this.option = option;
        }

        /** What precedes the quoted prompt, empty for a positional one. */
        String prefix() {
            return option.isEmpty() ? "" : option + " ";
        }
    }

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
        return executable + " " + promptStyle.prefix() + quote(task.prompt());
    }

    private static String quote(String argument) {
        return "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
