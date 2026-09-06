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

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What the panel hands an agent. The prompt is the whole contract between this plugin and a skill in
 * another repository, so the phrase it contains is worth pinning: change it and the skill stops
 * triggering, and the agent starts from nothing with no error anywhere.
 */
public class AgentCliTest {

    private static final String PROFILE_ID = "01a0769f-a1bb-744f-962d-88b314030196";

    @Test
    public void offersClaudeAndCodex() {
        assertEquals(List.of("Claude", "Codex"), AgentCli.ALL.stream().map(AgentCli::displayName).toList());
        assertEquals(List.of("claude", "codex"), AgentCli.ALL.stream().map(AgentCli::executable).toList());
    }

    /**
     * The phrase analyze-jfr's description fires on. Both agents get the same prompt — the skill ships
     * to Claude Code and to Codex from one source, and only the MCP tool prefix differs.
     */
    @Test
    public void sendsTheProfileIdAndTheSkillsTriggerPhrase() {
        assertEquals("Analyse Jeffrey profile " + PROFILE_ID, AgentCli.prompt(PROFILE_ID, false));
    }

    /**
     * No baked-in question. The panel does not know what the developer wants to know, and a recording
     * that lost a third of its samples is the case that proves it: an opener about where the time goes
     * would have the agent rank hot paths that are biased exactly where it matters.
     */
    @Test
    public void asksNoQuestionOfItsOwn() {
        String prompt = AgentCli.prompt(PROFILE_ID, false);
        assertFalse(prompt.contains("?"));
        assertFalse(prompt.contains("—"));
    }

    /** And never the file path: neither agent can parse a JFR. */
    @Test
    public void neverMentionsTheRecordingFile() {
        assertFalse(AgentCli.prompt(PROFILE_ID, false).contains(".jfr"));
    }

    /**
     * A heap dump goes to analyze-heap, which fires on "a heap dump". Sending one to the recording
     * skill would have the agent reach for flamegraph tools against a profile that has none.
     */
    @Test
    public void namesAHeapDumpSoTheHeapSkillTriggers() {
        String prompt = AgentCli.prompt(PROFILE_ID, true);
        assertTrue(prompt.contains("heap dump"));
        assertTrue(prompt.contains(PROFILE_ID));
        assertFalse(prompt.contains(".hprof"));
    }

    @Test
    public void quotesThePromptAsASingleArgument() {
        assertEquals(
                "claude \"Analyse Jeffrey profile " + PROFILE_ID + "\"",
                AgentCli.ALL.getFirst().command(PROFILE_ID, false));
        assertEquals(
                "codex \"Analyse Jeffrey profile " + PROFILE_ID + "\"",
                AgentCli.ALL.getLast().command(PROFILE_ID, false));
    }

    /**
     * A command assembled by concatenation and run in the developer's shell is not the place to
     * assume well-formed input, even though only a profile id ever reaches it.
     */
    @Test
    public void escapesQuotesAndBackslashes() {
        String command = AgentCli.ALL.getFirst().command("a\"b\\c", false);
        assertEquals("claude \"Analyse Jeffrey profile a\\\"b\\\\c\"", command);
        assertTrue(command.startsWith("claude \""));
        assertTrue(command.endsWith("\""));
    }
}
