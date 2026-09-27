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
 * @param promptStyle   how that command takes the opening sentence
 * @param endpointStyle how that command is told which Microscope to talk to
 */
public record AgentCli(String displayName, String executable, PromptStyle promptStyle, EndpointStyle endpointStyle) {

    public static final List<AgentCli> ALL = List.of(
            new AgentCli("Claude", "claude", PromptStyle.POSITIONAL, EndpointStyle.MCP_CONFIG_OPTION),
            new AgentCli("Codex", "codex", PromptStyle.POSITIONAL, EndpointStyle.CONFIG_OVERRIDE_OPTION),
            new AgentCli("Gemini", "gemini", PromptStyle.INTERACTIVE_OPTION, EndpointStyle.ENVIRONMENT_VARIABLE));

    /** Where Microscope serves MCP, appended to the configured Microscope address. */
    private static final String MCP_PATH = "/api/mcp";

    /**
     * The server name the skills are written against: {@code mcp__jeffrey__} in Claude Code and
     * Codex, {@code mcp_jeffrey_} in Gemini are all built from it.
     */
    private static final String SERVER_NAME = "jeffrey";

    /** The variable the Gemini extension's server entry reads its endpoint from. */
    private static final String GEMINI_ENDPOINT_VARIABLE = "JEFFREY_MCP_ENDPOINT";

    /** The variable a Jeffrey bearer token is read from outside the Claude Code plugin's own setting. */
    private static final String TOKEN_VARIABLE = "JEFFREY_MCP_TOKEN";

    /**
     * The {@code Authorization} value handed to Claude: a reference Claude Code expands itself when it
     * reads the configuration, empty when the variable is unset. It must reach Claude verbatim, and
     * does in every shell {@link ShellQuoting} knows — see {@link EndpointStyle}.
     */
    private static final String CLAUDE_AUTHORIZATION = "Bearer ${" + TOKEN_VARIABLE + ":-}";

    /**
     * How an agent is told which Microscope to use.
     *
     * <p>Without it an agent launched from the panel talks to whatever its own configuration points
     * at — the plugin's default {@code localhost:8585} — while the panel talks to the address in
     * the settings, and a developer who moved Microscope to another port gets an agent that cannot
     * find the profile the panel just showed them. Each CLI takes the address differently, so each
     * gets a style, the way {@link PromptStyle} carries the difference in how they take a prompt.
     *
     * <p>Claude and Codex are told only when the address is not the default: at the default the
     * {@code microscope} plugin's own registration already points there, and a second {@code jeffrey}
     * server would register every tool twice; anywhere else the plugin's copy points at nothing.
     * Gemini's variable is always set — its extension reads it, and nothing registers twice.
     *
     * <p>The server registered in place of the plugin's also has to carry the token the plugin's
     * would have sent, or a Jeffrey that sets {@code jeffrey.microscope.mcp.token} refuses every tool.
     * So it forwards {@code JEFFREY_MCP_TOKEN} from the environment the terminal inherits from the IDE:
     * Claude gets an {@code Authorization} header holding {@code ${JEFFREY_MCP_TOKEN:-}}, which Claude
     * Code expands, and Codex {@code bearer_token_env_var}, the name of the variable to read. The
     * reference stays unexpanded on its way through the shell in all three quotings — POSIX single
     * quotes and a PowerShell literal expand nothing, and {@code cmd.exe} expands only
     * {@code %NAME%} — so it is emitted for every shell. Codex's value is left unquoted, as its URL
     * is: {@code -c} takes a value that is not TOML as a literal string, and no quote then has to
     * survive a Windows shell.
     */
    public enum EndpointStyle {

        /** Claude Code: an inline {@code --mcp-config} with one HTTP server. */
        MCP_CONFIG_OPTION {
            @Override
            String options(String microscopeUrl, ShellQuoting quoting) {
                if (isDefault(microscopeUrl)) {
                    return "";
                }
                String json = "{\"mcpServers\":{\"" + SERVER_NAME + "\":{\"type\":\"http\",\"url\":\""
                        + jsonEscape(endpoint(microscopeUrl)) + "\",\"headers\":{\"Authorization\":\""
                        + CLAUDE_AUTHORIZATION + "\"}}}}";
                return "--mcp-config " + quoting.quote(json) + " ";
            }
        },

        /** Codex: {@code -c} overrides of the server's URL and of the variable its token is read from. */
        CONFIG_OVERRIDE_OPTION {
            @Override
            String options(String microscopeUrl, ShellQuoting quoting) {
                if (isDefault(microscopeUrl)) {
                    return "";
                }
                return "-c " + quoting.quote("mcp_servers." + SERVER_NAME + ".url=" + endpoint(microscopeUrl)) + " "
                        + "-c " + quoting.quote("mcp_servers." + SERVER_NAME + ".bearer_token_env_var=" + TOKEN_VARIABLE)
                        + " ";
            }
        },

        /** Gemini CLI: its extension reads the endpoint from the environment. */
        ENVIRONMENT_VARIABLE {
            @Override
            String environment(String microscopeUrl, ShellQuoting quoting) {
                return quoting.environment(GEMINI_ENDPOINT_VARIABLE, endpoint(microscopeUrl));
            }
        };

        /** Assignments that precede the executable, empty when there are none. */
        String environment(String microscopeUrl, ShellQuoting quoting) {
            return "";
        }

        /** Options that follow the executable, empty when there are none. */
        String options(String microscopeUrl, ShellQuoting quoting) {
            return "";
        }

        private static boolean isDefault(String microscopeUrl) {
            return JeffreySettings.DEFAULT_MICROSCOPE_URL.equals(microscopeUrl);
        }

        private static String endpoint(String microscopeUrl) {
            return microscopeUrl + MCP_PATH;
        }
    }

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
     * The command line, pointed at the configured Microscope, with the task's sentence as one
     * argument. Only profile ids ever reach the prompt, but every argument is quoted anyway, and by
     * the rules of the shell the command is typed into: the address is typed by the developer into
     * the settings, and a command assembled by string concatenation and run in their shell is not
     * the place to assume well-formed input.
     *
     * @param microscopeUrl the Microscope address from the settings, without a trailing slash
     * @param quoting       the quoting of the shell the command will be typed into
     */
    public String command(AgentTask task, String microscopeUrl, ShellQuoting quoting) {
        return endpointStyle.environment(microscopeUrl, quoting)
                + executable + " "
                + endpointStyle.options(microscopeUrl, quoting)
                + promptStyle.prefix()
                + quoting.quote(task.prompt());
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
