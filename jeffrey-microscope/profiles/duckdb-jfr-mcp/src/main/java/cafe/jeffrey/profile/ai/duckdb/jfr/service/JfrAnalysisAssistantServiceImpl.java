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

package cafe.jeffrey.profile.ai.duckdb.jfr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.AssistantResponse;
import cafe.jeffrey.profile.ai.chat.McpToolset;
import cafe.jeffrey.profile.ai.chat.McpToolsetFactory;
import cafe.jeffrey.profile.ai.chat.ToolBinding;
import cafe.jeffrey.profile.ai.chat.ToolCallResult;
import cafe.jeffrey.profile.ai.chat.ToolExchange;
import cafe.jeffrey.profile.ai.duckdb.jfr.model.JfrAnalysisRequest;
import cafe.jeffrey.profile.ai.duckdb.jfr.prompt.JfrAnalysisSystemPrompt;
import cafe.jeffrey.profile.ai.duckdb.jfr.tools.DuckDbMcpTools;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Implementation of JFR Analysis Assistant using Spring AI with DuckDB tools for direct database access.
 */
public class JfrAnalysisAssistantServiceImpl implements JfrAnalysisAssistantService {

    private static final Logger LOG = LoggerFactory.getLogger(JfrAnalysisAssistantServiceImpl.class);

    private static final String AI_CALL_SPAN_NAME = "ai.jfr-analysis.call";

    private final AiChatBackend chatBackend;
    private final DatabaseManagerResolver databaseManagerResolver;
    private final McpToolsetFactory mcpToolsetFactory;

    public JfrAnalysisAssistantServiceImpl(
            AiChatBackend chatBackend,
            DatabaseManagerResolver databaseManagerResolver,
            McpToolsetFactory mcpToolsetFactory) {
        this.chatBackend = chatBackend;
        this.databaseManagerResolver = databaseManagerResolver;
        this.mcpToolsetFactory = mcpToolsetFactory;
        LOG.info("JFR Analysis Assistant initialized with DuckDB tools: provider={} model={}",
                chatBackend.providerName(), chatBackend.modelName());
    }

    @Override
    public boolean isAvailable() {
        return chatBackend.isAvailable();
    }

    @Override
    public String getModelName() {
        return chatBackend.modelName();
    }

    @Override
    public String getProviderName() {
        return chatBackend.providerName();
    }

    @Override
    public AssistantResponse analyze(ProfileInfo profileInfo, JfrAnalysisRequest request) {
        try {
            // Get DataSource for this profile
            DataSource dataSource = databaseManagerResolver.open(profileInfo);

            // Create tools for this profile's database (with modification support if enabled)
            DuckDbMcpTools tools = new DuckDbMcpTools(dataSource, request.canModify());

            if (request.canModify()) {
                LOG.info("Data modification enabled for analysis: profileId={}", profileInfo.id());
            }

            // Build dynamic system prompt with actual DB schema
            String eventsSchema = queryEventsTableSchema(dataSource);
            String systemPrompt = JfrAnalysisSystemPrompt.buildPrompt(eventsSchema);

            // Bind both tool representations; the active backend uses the one it understands.
            McpToolset mcpToolset = mcpToolsetFactory.forJfr(profileInfo.id());
            ToolBinding toolBinding = new ToolBinding(tools, mcpToolset);

            // Call the AI with tool support
            String contextualMessage = buildContextualMessage(request, profileInfo);
            ToolExchange exchange = new ToolExchange(
                    systemPrompt, request.history(), contextualMessage, toolBinding, AI_CALL_SPAN_NAME);
            ToolCallResult result = chatBackend.analyze(exchange);

            // Generate follow-up suggestions
            List<String> suggestions = generateSuggestions(request.message(), result.text());

            return new AssistantResponse(result.text(), suggestions, result.toolsUsed());

        } catch (Exception e) {
            LOG.error("Error during JFR analysis: profileId={} message={}", profileInfo.id(), e.getMessage(), e);
            return AssistantResponse.error("Analysis failed: " + e.getMessage());
        }
    }

    private String buildContextualMessage(JfrAnalysisRequest request, ProfileInfo profileInfo) {
        return """
                Profile: %s (ID: %s)
                Duration: %s

                User Question: %s
                """.formatted(
                profileInfo.name(),
                profileInfo.id(),
                formatDuration(profileInfo),
                request.message()
        );
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

    private static final Map<String, String> COLUMN_ANNOTATIONS = Map.of(
            "duration", " (nanoseconds)"
    );

    private String queryEventsTableSchema(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, "events", "%")) {
                StringJoiner joiner = new StringJoiner(", ");
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    String annotation = COLUMN_ANNOTATIONS.getOrDefault(columnName, "");
                    joiner.add(columnName + " " + typeName + annotation);
                }
                String schema = joiner.toString();
                if (schema.isEmpty()) {
                    LOG.warn("No columns found for events table, falling back to static schema");
                    return "event_type VARCHAR, start_timestamp TIMESTAMPTZ, duration BIGINT (nanoseconds), "
                            + "samples BIGINT, weight BIGINT, weight_entity VARCHAR, "
                            + "stacktrace_hash BIGINT, thread_hash BIGINT, fields JSON";
                }
                return schema;
            }
        } catch (SQLException e) {
            LOG.warn("Failed to query events table schema, falling back to static schema: message={}", e.getMessage());
            return "event_type VARCHAR, start_timestamp TIMESTAMPTZ, duration BIGINT (nanoseconds), "
                    + "samples BIGINT, weight BIGINT, weight_entity VARCHAR, "
                    + "stacktrace_hash BIGINT, thread_hash BIGINT, fields JSON";
        }
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
