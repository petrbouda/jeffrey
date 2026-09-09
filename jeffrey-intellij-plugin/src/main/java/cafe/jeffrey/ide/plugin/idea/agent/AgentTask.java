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

/**
 * What the panel asks a coding agent to do, as the sentence it is asked in.
 *
 * <p><b>Profile ids, never file paths.</b> Neither Claude Code nor Codex can parse a JFR or an
 * hprof, and Microscope has already done it — a path would send the agent to read bytes it cannot
 * make sense of, when an id reaches the parsed profile through the MCP server.
 *
 * <p><b>No question of its own.</b> The method lives in the skill each sentence triggers, and the
 * panel does not know what the developer wants to ask. A recording that lost a third of its samples
 * is the case that proves it: an opener like "where is the time going?" would have the agent rank
 * hot paths that are biased exactly where it matters, instead of noticing the loss first.
 *
 * <p>Three implementations rather than one sentence with flags, because each wording is the trigger
 * for a different skill — {@code analyze-jfr}, {@code analyze-heap}, {@code compare-jfr} — and a
 * boolean parameter that silently picks between skills is how a heap dump ends up being asked about
 * with flamegraph tools. Sealed, so the compiler names the place to add the fourth.
 */
public sealed interface AgentTask {

    /** The sentence handed to the agent, quoted as one argument by {@link AgentCli#command}. */
    String prompt();

    /** A recording: {@code analyze-jfr} fires on "a Jeffrey profile". */
    record AnalyseRecording(String profileId) implements AgentTask {

        @Override
        public String prompt() {
            return "Analyse Jeffrey profile " + profileId;
        }
    }

    /** A heap dump: {@code analyze-heap} fires on "a heap dump". */
    record AnalyseHeapDump(String profileId) implements AgentTask {

        @Override
        public String prompt() {
            return "Analyse the heap dump in Jeffrey profile " + profileId;
        }
    }

    /**
     * A pair: {@code compare-jfr} fires on comparing two profiles.
     *
     * <p>The primary is named first and the baseline is named as the baseline, because that is the
     * one thing about a comparison the wording has to carry. Read the other way round the skill
     * would pass the two ids to {@code compare_movements} reversed, and every regression in the
     * report would read as an improvement.
     */
    record Compare(String profileId, String baselineProfileId) implements AgentTask {

        @Override
        public String prompt() {
            return "Compare Jeffrey profile " + profileId + " against baseline " + baselineProfileId;
        }
    }
}
