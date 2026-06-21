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

import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.ChatExchange;
import cafe.jeffrey.profile.ai.chat.ChatMessage;
import cafe.jeffrey.profile.ai.chat.McpToolset;
import cafe.jeffrey.profile.ai.chat.ToolCallResult;
import cafe.jeffrey.profile.ai.chat.ToolExchange;
import cafe.jeffrey.shared.common.span.Spans;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * {@link AiChatBackend} implementation backed by the Claude Code CLI in headless mode. Authentication
 * reuses the host's Claude subscription, so no API key is required. Prompt-only calls run the CLI
 * directly; tool-enabled calls point the CLI at an in-JVM MCP server (described by the request's
 * {@link McpToolset}) restricted to that server's tools, so the model cannot touch the host
 * filesystem or shell.
 */
public final class ClaudeCodeChatBackend implements AiChatBackend {

    private static final Logger LOG = LoggerFactory.getLogger(ClaudeCodeChatBackend.class);

    private static final String PROVIDER_DISPLAY_NAME = "Claude Code";
    private static final String MCP_SERVERS_FIELD = "mcpServers";
    private static final String MCP_TYPE_HTTP = "http";

    private final ClaudeCodeCliClient cliClient;
    private final String modelName;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaudeCodeChatBackend(ClaudeCodeCliClient cliClient, String modelName) {
        this.cliClient = cliClient;
        this.modelName = modelName;
    }

    @Override
    public boolean isAvailable() {
        return cliClient.isAvailable();
    }

    @Override
    public String providerName() {
        return PROVIDER_DISPLAY_NAME;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public String chat(ChatExchange exchange, String spanName) {
        String prompt = buildPrompt(exchange.history(), exchange.userMessage());
        ClaudeCodeRequest request = ClaudeCodeRequest.promptOnly(prompt, exchange.systemPrompt(), modelName);

        long span = Spans.start();
        try {
            ClaudeCodeResult result = cliClient.run(request);
            return result.text();
        } finally {
            Spans.end(span, spanName);
        }
    }

    @Override
    public ToolCallResult analyze(ToolExchange exchange) {
        String prompt = buildPrompt(exchange.history(), exchange.userMessage());
        McpToolset toolset = exchange.toolBinding().mcpToolset();

        ClaudeCodeRequest request;
        if (toolset == null) {
            LOG.warn("Claude Code analysis requested without an MCP toolset; running without tool access");
            request = ClaudeCodeRequest.promptOnly(prompt, exchange.systemPrompt(), modelName);
        } else {
            request = new ClaudeCodeRequest(
                    prompt,
                    exchange.systemPrompt(),
                    modelName,
                    buildMcpConfigJson(toolset),
                    toolset.allowedTools());
        }

        long span = Spans.start();
        try {
            ClaudeCodeResult result = cliClient.run(request);
            return new ToolCallResult(result.text(), result.toolsUsed());
        } finally {
            Spans.end(span, exchange.spanName());
        }
    }

    private String buildMcpConfigJson(McpToolset toolset) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode servers = root.putObject(MCP_SERVERS_FIELD);
        ObjectNode server = servers.putObject(toolset.serverName());
        server.put("type", MCP_TYPE_HTTP);
        server.put("url", toolset.url());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize MCP config", e);
        }
    }

    private static String buildPrompt(List<ChatMessage> history, String userMessage) {
        if (history == null || history.isEmpty()) {
            return userMessage;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Previous conversation:\n");
        for (ChatMessage message : history) {
            sb.append(message.isUser() ? "[User] " : "[Assistant] ")
                    .append(message.content())
                    .append("\n");
        }
        sb.append("\nCurrent request:\n").append(userMessage);
        return sb.toString();
    }
}
