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
import static org.junit.Assert.assertTrue;

/**
 * Which agents the panel knows, and how it spells a command for one. What the command <em>says</em>
 * is {@link AgentTask}'s, and pinned in its own test.
 */
public class AgentCliTest {

    private static final String PROFILE_ID = "01a0769f-a1bb-744f-962d-88b314030196";

    @Test
    public void offersClaudeCodexAndGemini() {
        assertEquals(
                List.of("Claude", "Codex", "Gemini"),
                AgentCli.ALL.stream().map(AgentCli::displayName).toList());
        assertEquals(
                List.of("claude", "codex", "gemini"),
                AgentCli.ALL.stream().map(AgentCli::executable).toList());
    }

    @Test
    public void quotesThePromptAsASingleArgument() {
        assertEquals(
                "claude \"Analyse Jeffrey profile " + PROFILE_ID + "\"",
                agent("claude").command(new AgentTask.AnalyseRecording(PROFILE_ID)));
        assertEquals(
                "codex \"Analyse Jeffrey profile " + PROFILE_ID + "\"",
                agent("codex").command(new AgentTask.AnalyseRecording(PROFILE_ID)));
    }

    /**
     * Gemini reads a positional prompt as a batch run: it would answer into a terminal tab and exit,
     * where the panel is handing over a profile to talk about. {@code -i} is what keeps the session.
     */
    @Test
    public void asksGeminiForAnInteractiveSession() {
        assertEquals(
                "gemini -i \"Analyse Jeffrey profile " + PROFILE_ID + "\"",
                agent("gemini").command(new AgentTask.AnalyseRecording(PROFILE_ID)));
    }

    /** Whatever an agent puts before the prompt, the prompt itself stays one argument. */
    @Test
    public void everyAgentEndsWithTheQuotedPrompt() {
        for (AgentCli agent : AgentCli.ALL) {
            String command = agent.command(new AgentTask.AnalyseRecording(PROFILE_ID));
            assertTrue(command.startsWith(agent.executable() + " "));
            assertTrue(command.endsWith("\"Analyse Jeffrey profile " + PROFILE_ID + "\""));
        }
    }

    /**
     * A command assembled by concatenation and run in the developer's shell is not the place to
     * assume well-formed input, even though only a profile id ever reaches it.
     */
    @Test
    public void escapesQuotesAndBackslashes() {
        String command = agent("claude").command(new AgentTask.AnalyseRecording("a\"b\\c"));
        assertEquals("claude \"Analyse Jeffrey profile a\\\"b\\\\c\"", command);
        assertTrue(command.startsWith("claude \""));
        assertTrue(command.endsWith("\""));
    }

    private static AgentCli agent(String executable) {
        return AgentCli.ALL.stream()
                .filter(candidate -> candidate.executable().equals(executable))
                .findFirst()
                .orElseThrow();
    }
}
