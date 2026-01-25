/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.ai.mcp.service;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.function.FunctionCallback;
import pbouda.jeffrey.profile.ai.mcp.model.JfrAnalysisRequest;
import pbouda.jeffrey.profile.ai.mcp.model.JfrAnalysisResponse;
import pbouda.jeffrey.profile.ai.mcp.model.JfrChatMessage;
import pbouda.jeffrey.profile.ai.mcp.prompt.JfrAnalysisSystemPrompt;
import pbouda.jeffrey.profile.ai.mcp.tools.DuckDbMcpTools;
import pbouda.jeffrey.provider.profile.DatabaseManagerResolver;
import pbouda.jeffrey.shared.common.model.ProfileInfo;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of JFR Analysis Assistant using Spring AI with MCP tools for DuckDB access.
 */
public class JfrAnalysisAssistantServiceImpl implements JfrAnalysisAssistantService {

    private static final Logger LOG = LoggerFactory.getLogger(JfrAnalysisAssistantServiceImpl.class);

    private final ChatClient chatClient;
    private final DatabaseManagerResolver databaseManagerResolver;

    public JfrAnalysisAssistantServiceImpl(
            ChatClient.Builder chatClientBuilder,
            DatabaseManagerResolver databaseManagerResolver) {
        this.chatClient = chatClientBuilder
                .defaultSystem(JfrAnalysisSystemPrompt.SYSTEM_PROMPT)
                .build();
        this.databaseManagerResolver = databaseManagerResolver;
        LOG.info("JFR Analysis Assistant initialized with MCP tools");
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public JfrAnalysisResponse analyze(ProfileInfo profileInfo, JfrAnalysisRequest request) {
        try {
            // Get DataSource for this profile
            DataSource dataSource = databaseManagerResolver.open(profileInfo);

            // Create MCP tools for this profile's database
            DuckDbMcpTools mcpTools = new DuckDbMcpTools(dataSource);
            List<SyncToolSpecification> toolSpecs = mcpTools.getToolSpecifications();

            // Convert MCP tools to Spring AI function callbacks
            List<FunctionCallback> functionCallbacks = convertToFunctionCallbacks(toolSpecs);

            // Build conversation messages
            List<Message> messages = buildMessages(request, profileInfo);

            // Call the AI with tool support
            List<String> toolsUsed = new ArrayList<>();
            String response = executeWithTools(messages, functionCallbacks, toolsUsed);

            // Generate follow-up suggestions
            List<String> suggestions = generateSuggestions(request.message(), response);

            return new JfrAnalysisResponse(response, suggestions, toolsUsed);

        } catch (Exception e) {
            LOG.error("Error during JFR analysis: profileId={} message={}", profileInfo.id(), e.getMessage(), e);
            return JfrAnalysisResponse.error("Analysis failed: " + e.getMessage());
        }
    }

    private List<FunctionCallback> convertToFunctionCallbacks(List<SyncToolSpecification> toolSpecs) {
        List<FunctionCallback> callbacks = new ArrayList<>();

        for (SyncToolSpecification spec : toolSpecs) {
            FunctionCallback callback = FunctionCallback.builder()
                    .function(spec.tool().name(), (Map<String, Object> args) -> {
                        CallToolResult result = spec.call().apply(args);
                        // Extract text content from the result
                        StringBuilder sb = new StringBuilder();
                        for (var content : result.content()) {
                            if (content instanceof TextContent textContent) {
                                sb.append(textContent.text());
                            }
                        }
                        return sb.toString();
                    })
                    .description(spec.tool().description())
                    .inputType(Map.class)
                    .build();
            callbacks.add(callback);
        }

        return callbacks;
    }

    private List<Message> buildMessages(JfrAnalysisRequest request, ProfileInfo profileInfo) {
        List<Message> messages = new ArrayList<>();

        // Add conversation history
        if (request.history() != null) {
            for (JfrChatMessage msg : request.history()) {
                if (msg.isUser()) {
                    messages.add(new UserMessage(msg.content()));
                } else {
                    messages.add(new AssistantMessage(msg.content()));
                }
            }
        }

        // Add current user message with profile context
        String contextualMessage = """
                Profile: %s (ID: %s)
                Duration: %s

                User Question: %s
                """.formatted(
                profileInfo.name(),
                profileInfo.id(),
                formatDuration(profileInfo),
                request.message()
        );

        messages.add(new UserMessage(contextualMessage));

        return messages;
    }

    private String formatDuration(ProfileInfo profileInfo) {
        if (profileInfo.profilingStartedAt() != null && profileInfo.profilingFinishedAt() != null) {
            long seconds = profileInfo.duration().toSeconds();
            if (seconds < 60) {
                return seconds + " seconds";
            } else if (seconds < 3600) {
                return (seconds / 60) + " minutes " + (seconds % 60) + " seconds";
            } else {
                return (seconds / 3600) + " hours " + ((seconds % 3600) / 60) + " minutes";
            }
        }
        return "unknown";
    }

    private String executeWithTools(List<Message> messages, List<FunctionCallback> functionCallbacks, List<String> toolsUsed) {
        var promptSpec = chatClient.prompt()
                .messages(messages)
                .functions(functionCallbacks.toArray(new FunctionCallback[0]));

        ChatResponse response = promptSpec.call().chatResponse();

        // Track which tools were used
        if (response.getResult().getMetadata() != null) {
            // Note: Tool usage tracking depends on the specific ChatModel implementation
            // Some models expose this in metadata, others don't
        }

        return response.getResult().getOutput().getText();
    }

    private List<String> generateSuggestions(String question, String response) {
        List<String> suggestions = new ArrayList<>();
        String lowerQuestion = question.toLowerCase();
        String lowerResponse = response.toLowerCase();

        // Suggest related analyses based on the conversation
        if (lowerQuestion.contains("cpu") || lowerQuestion.contains("execution")) {
            suggestions.add("Show me the hottest methods by sample count");
            suggestions.add("Which threads consumed the most CPU time?");
        }

        if (lowerQuestion.contains("memory") || lowerQuestion.contains("allocation")) {
            suggestions.add("What are the top allocation sites?");
            suggestions.add("Show me allocation patterns over time");
        }

        if (lowerQuestion.contains("gc") || lowerQuestion.contains("garbage")) {
            suggestions.add("What is the average GC pause time?");
            suggestions.add("Show me GC pause distribution");
        }

        if (lowerResponse.contains("thread") && !lowerQuestion.contains("thread")) {
            suggestions.add("Show me thread activity breakdown");
        }

        // General suggestions if we don't have specific ones
        if (suggestions.isEmpty()) {
            suggestions.add("What are the main performance bottlenecks?");
            suggestions.add("Show me a summary of all event types");
            suggestions.add("Analyze CPU usage patterns");
        }

        return suggestions.stream().limit(3).toList();
    }
}
