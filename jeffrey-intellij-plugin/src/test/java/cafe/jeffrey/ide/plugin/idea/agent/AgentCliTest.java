/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.ide.plugin.idea.agent;

import cafe.jeffrey.ide.plugin.idea.settings.JeffreySettings;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Which agents the panel knows, and how it spells a command for one. What the command <em>says</em>
 * is {@link AgentTask}'s, and pinned in its own test; how each shell quotes is {@link ShellQuoting}'s.
 */
public class AgentCliTest {

    private static final String PROFILE_ID = "01a0769f-a1bb-744f-962d-88b314030196";
    private static final String MICROSCOPE_URL = "http://localhost:9090";
    private static final String MCP_ENDPOINT = MICROSCOPE_URL + "/api/mcp";
    private static final String DEFAULT_ENDPOINT = JeffreySettings.DEFAULT_MICROSCOPE_URL + "/api/mcp";
    private static final String PROMPT = "'Analyse Jeffrey profile " + PROFILE_ID + "'";
    private static final String WINDOWS_PROMPT = "\"Analyse Jeffrey profile " + PROFILE_ID + "\"";

    /** What Claude Code must receive verbatim and expand itself, from the IDE's environment. */
    private static final String TOKEN_REFERENCE = "${JEFFREY_MCP_TOKEN:-}";
    private static final String CODEX_TOKEN_OPTION = "mcp_servers.jeffrey.bearer_token_env_var=JEFFREY_MCP_TOKEN";

    @Test
    public void offersClaudeCodexAndGemini() {
        assertEquals(
                List.of("Claude", "Codex", "Gemini"),
                AgentCli.ALL.stream().map(AgentCli::displayName).toList());
        assertEquals(
                List.of("claude", "codex", "gemini"),
                AgentCli.ALL.stream().map(AgentCli::executable).toList());
    }

    /**
     * The configured Microscope reaches Claude as an inline MCP configuration, so an agent launched
     * from a panel pointed at another port or host talks to the same Microscope the panel does.
     */
    @Test
    public void pointsClaudeAtAMovedMicroscope() {
        assertEquals(
                "claude --mcp-config '{\"mcpServers\":{\"jeffrey\":{\"type\":\"http\",\"url\":\""
                        + MCP_ENDPOINT + "\",\"headers\":{\"Authorization\":\"Bearer " + TOKEN_REFERENCE
                        + "\"}}}}' " + PROMPT,
                posix("claude", MICROSCOPE_URL));
    }

    @Test
    public void pointsCodexAtAMovedMicroscope() {
        assertEquals(
                "codex -c 'mcp_servers.jeffrey.url=" + MCP_ENDPOINT + "' -c '" + CODEX_TOKEN_OPTION + "' " + PROMPT,
                posix("codex", MICROSCOPE_URL));
    }

    /**
     * The plugin's own server is replaced by the one the launcher registers, so the token it would
     * have sent has to come with it: a Jeffrey that sets {@code jeffrey.microscope.mcp.token} would
     * otherwise refuse every tool the agent calls. Claude gets a header that it expands itself from
     * {@code JEFFREY_MCP_TOKEN}, Codex the name of the variable to read.
     */
    @Test
    public void forwardsTheTokenVariableToAMovedMicroscopeInEveryShell() {
        assertTrue(command("claude", MICROSCOPE_URL, ShellQuoting.POWERSHELL).startsWith(
                "claude --mcp-config '{\\\"mcpServers\\\":{\\\"jeffrey\\\":{\\\"type\\\":\\\"http\\\","
                        + "\\\"url\\\":\\\"" + MCP_ENDPOINT + "\\\",\\\"headers\\\":{\\\"Authorization\\\":"
                        + "\\\"Bearer " + TOKEN_REFERENCE + "\\\"}}}}' "));
        assertEquals(
                "codex -c 'mcp_servers.jeffrey.url=" + MCP_ENDPOINT + "' -c '" + CODEX_TOKEN_OPTION + "' "
                        + "'Analyse Jeffrey profile " + PROFILE_ID + "'",
                command("codex", MICROSCOPE_URL, ShellQuoting.POWERSHELL));
        assertEquals(
                "codex -c \"mcp_servers.jeffrey.url=" + MCP_ENDPOINT + "\" -c \"" + CODEX_TOKEN_OPTION + "\" "
                        + WINDOWS_PROMPT,
                command("codex", MICROSCOPE_URL, ShellQuoting.CMD));
    }

    /**
     * The shell must not expand the reference: Claude Code does, when it reads the configuration. A
     * POSIX shell keeps it inside single quotes, PowerShell inside a single-quoted literal, and
     * cmd.exe expands only {@code %NAME%}, never {@code $}, so all three hand it over verbatim.
     */
    @Test
    public void leavesTheTokenReferenceForClaudeToExpand() {
        for (ShellQuoting quoting : ShellQuoting.values()) {
            String command = command("claude", MICROSCOPE_URL, quoting);
            assertTrue(quoting + ": " + command, command.contains("Bearer " + TOKEN_REFERENCE));
        }
    }

    /** The plugin's own registration sends the token at the default address; nothing is added there. */
    @Test
    public void sendsNoTokenOptionAtTheDefaultMicroscope() {
        for (ShellQuoting quoting : ShellQuoting.values()) {
            assertFalse(command("claude", JeffreySettings.DEFAULT_MICROSCOPE_URL, quoting).contains("JEFFREY_MCP_TOKEN"));
            assertFalse(command("codex", JeffreySettings.DEFAULT_MICROSCOPE_URL, quoting).contains("JEFFREY_MCP_TOKEN"));
        }
    }

    /**
     * At the default address the plugin's own {@code jeffrey} server already points there; a second
     * registration would advertise every tool twice.
     */
    @Test
    public void leavesClaudeAndCodexAloneAtTheDefaultMicroscope() {
        assertEquals("claude " + PROMPT, posix("claude", JeffreySettings.DEFAULT_MICROSCOPE_URL));
        assertEquals("codex " + PROMPT, posix("codex", JeffreySettings.DEFAULT_MICROSCOPE_URL));
    }

    /** Gemini's extension reads its endpoint from the environment, not from a flag — always set. */
    @Test
    public void pointsGeminiAtTheConfiguredMicroscope() {
        assertEquals(
                "JEFFREY_MCP_ENDPOINT='" + MCP_ENDPOINT + "' gemini -i " + PROMPT,
                posix("gemini", MICROSCOPE_URL));
        assertEquals(
                "JEFFREY_MCP_ENDPOINT='" + DEFAULT_ENDPOINT + "' gemini -i " + PROMPT,
                posix("gemini", JeffreySettings.DEFAULT_MICROSCOPE_URL));
    }

    @Test
    public void setsGeminisVariableTheWayEachWindowsShellDoes() {
        assertEquals(
                "$env:JEFFREY_MCP_ENDPOINT='" + MCP_ENDPOINT + "'; gemini -i 'Analyse Jeffrey profile "
                        + PROFILE_ID + "'",
                command("gemini", MICROSCOPE_URL, ShellQuoting.POWERSHELL));
        assertEquals(
                "set \"JEFFREY_MCP_ENDPOINT=" + MCP_ENDPOINT + "\" && gemini -i " + WINDOWS_PROMPT,
                command("gemini", MICROSCOPE_URL, ShellQuoting.CMD));
    }

    /** On Windows the JSON's own double quotes reach Claude escaped, not stripped. */
    @Test
    public void pointsClaudeAtAMovedMicroscopeFromCmd() {
        assertEquals(
                "claude --mcp-config \"{\\\"mcpServers\\\":{\\\"jeffrey\\\":{\\\"type\\\":\\\"http\\\","
                        + "\\\"url\\\":\\\"" + MCP_ENDPOINT + "\\\",\\\"headers\\\":{\\\"Authorization\\\":"
                        + "\\\"Bearer " + TOKEN_REFERENCE + "\\\"}}}}\" " + WINDOWS_PROMPT,
                command("claude", MICROSCOPE_URL, ShellQuoting.CMD));
    }

    /**
     * Gemini reads a positional prompt as a batch run: it would answer into a terminal tab and exit,
     * where the panel is handing over a profile to talk about. {@code -i} is what keeps the session.
     */
    @Test
    public void asksGeminiForAnInteractiveSession() {
        assertTrue(posix("gemini", MICROSCOPE_URL).endsWith("gemini -i " + PROMPT));
    }

    /** Whatever an agent puts before the prompt, the prompt itself stays one argument. */
    @Test
    public void everyAgentEndsWithTheQuotedPrompt() {
        for (ShellQuoting quoting : ShellQuoting.values()) {
            for (AgentCli agent : AgentCli.ALL) {
                String command = agent.command(new AgentTask.AnalyseRecording(PROFILE_ID), MICROSCOPE_URL, quoting);
                assertTrue(command.contains(agent.executable() + " "));
                assertTrue(command.contains(MCP_ENDPOINT));
                assertTrue(command.endsWith(quoting.quote("Analyse Jeffrey profile " + PROFILE_ID)));
            }
        }
    }

    /** The URL is typed by the developer and run in their shell: it must stay one inert argument. */
    @Test
    public void quotesTheMicroscopeUrlForAPosixShell() {
        assertEquals(
                "codex -c 'mcp_servers.jeffrey.url=http://host/it'\\''s$(rm)/api/mcp' -c '" + CODEX_TOKEN_OPTION + "' "
                        + PROMPT,
                posix("codex", "http://host/it's$(rm)"));
    }

    /** The same URL typed into PowerShell: a single-quoted literal, the quote doubled, nothing expanded. */
    @Test
    public void quotesTheMicroscopeUrlForPowerShell() {
        assertEquals(
                "codex -c 'mcp_servers.jeffrey.url=http://host/it''s$(rm)/api/mcp' -c '" + CODEX_TOKEN_OPTION
                        + "' 'Analyse Jeffrey profile " + PROFILE_ID + "'",
                command("codex", "http://host/it's$(rm)", ShellQuoting.POWERSHELL));
    }

    /** And into cmd.exe, where a double-quoted argument keeps {@code $(...)} and a quote is inert. */
    @Test
    public void quotesTheMicroscopeUrlForCmd() {
        assertEquals(
                "codex -c \"mcp_servers.jeffrey.url=http://host/it's$(rm)/api/mcp\" -c \"" + CODEX_TOKEN_OPTION + "\" "
                        + WINDOWS_PROMPT,
                command("codex", "http://host/it's$(rm)", ShellQuoting.CMD));
    }

    /** On the Gemini path under cmd.exe, a URL that would break out of {@code set "…"} is refused. */
    @Test
    public void refusesAUrlCmdCannotHold() {
        assertThrows(IllegalArgumentException.class,
                () -> command("gemini", "http://host\"&calc&\"", ShellQuoting.CMD));
        assertThrows(IllegalArgumentException.class,
                () -> command("claude", "http://host&calc", ShellQuoting.CMD));
    }

    /** The same URL on PowerShell and POSIX is carried, inert, inside a literal. */
    @Test
    public void carriesAHostileUrlInertlyOutsideCmd() {
        assertEquals(
                "$env:JEFFREY_MCP_ENDPOINT='http://host\"&calc&\"/api/mcp'; gemini -i 'Analyse Jeffrey profile "
                        + PROFILE_ID + "'",
                command("gemini", "http://host\"&calc&\"", ShellQuoting.POWERSHELL));
        assertEquals(
                "JEFFREY_MCP_ENDPOINT='http://host\"&calc&\"/api/mcp' gemini -i " + PROMPT,
                posix("gemini", "http://host\"&calc&\""));
    }

    /** {@code host\\"x} must reach Codex as one argument on Windows, not close it after {@code host\\}. */
    @Test
    public void keepsABackslashBeforeAQuoteInsideOneWindowsArgument() {
        assertEquals(
                "codex -c \"mcp_servers.jeffrey.url=http://host\\\\\\\"x/api/mcp\" -c \"" + CODEX_TOKEN_OPTION + "\" "
                        + WINDOWS_PROMPT,
                command("codex", "http://host\\\"x", ShellQuoting.CMD));
        assertEquals(
                "codex -c 'mcp_servers.jeffrey.url=http://host\\\\\\\"x/api/mcp' -c '" + CODEX_TOKEN_OPTION
                        + "' 'Analyse Jeffrey profile " + PROFILE_ID + "'",
                command("codex", "http://host\\\"x", ShellQuoting.POWERSHELL));
    }

    /** A quote or a backslash in the URL cannot break out of the JSON string Claude is handed. */
    @Test
    public void escapesTheMicroscopeUrlInsideJson() {
        String command = posix("claude", "http://h\"x\\y");
        assertTrue(command.contains("\"url\":\"http://h\\\"x\\\\y/api/mcp\""));
    }

    /** A command assembled by concatenation is not the place to assume well-formed input. */
    @Test
    public void keepsAHostilePromptOneArgument() {
        String command = agent("codex").command(
                new AgentTask.AnalyseRecording("a'b$(rm)"), JeffreySettings.DEFAULT_MICROSCOPE_URL, ShellQuoting.POSIX);
        assertEquals("codex 'Analyse Jeffrey profile a'\\''b$(rm)'", command);
        assertFalse(command.contains("\"a'b"));
    }

    private static String posix(String executable, String microscopeUrl) {
        return command(executable, microscopeUrl, ShellQuoting.POSIX);
    }

    private static String command(String executable, String microscopeUrl, ShellQuoting quoting) {
        return agent(executable).command(new AgentTask.AnalyseRecording(PROFILE_ID), microscopeUrl, quoting);
    }

    private static AgentCli agent(String executable) {
        return AgentCli.ALL.stream()
                .filter(candidate -> candidate.executable().equals(executable))
                .findFirst()
                .orElseThrow();
    }
}
