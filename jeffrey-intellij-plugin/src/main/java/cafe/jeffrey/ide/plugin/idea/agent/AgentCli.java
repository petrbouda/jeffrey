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
     * The prompt both agents receive. Deliberately just the profile — no baked-in question.
     *
     * <p>The method lives in the skill, and the panel does not know what the developer wants to know.
     * A recording that lost a third of its samples is the case that proves it: an opener like "where
     * is the time going?" would have the agent rank hot paths that are biased exactly where it
     * matters, instead of noticing the loss first.
     *
     * <p>The <b>profileId</b>, never the file path: neither agent can parse a JFR or an hprof, and
     * Microscope has already done it.
     *
     * <p>The wording carries the phrase the right skill triggers on — {@code analyze-jfr} fires on "a
     * Jeffrey profile", {@code analyze-heap} on "a heap dump". Sending a dump to the recording skill
     * would have the agent reach for flamegraph tools against a profile that has none.
     */
    public static String prompt(String profileId, boolean heapDump) {
        return heapDump
                ? "Analyse the heap dump in Jeffrey profile " + profileId
                : "Analyse Jeffrey profile " + profileId;
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
     * The command line, with the prompt quoted as one argument. Only a profile id ever reaches the
     * quoting, but it is done properly anyway — a command assembled by string concatenation and run in
     * the developer's shell is not the place to assume well-formed input.
     */
    public String command(String profileId, boolean heapDump) {
        return executable + " " + quote(prompt(profileId, heapDump));
    }

    private static String quote(String argument) {
        return "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
