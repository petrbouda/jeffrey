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

package cafe.jeffrey.profile.ai.duckdb.jfr.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.AssistantResponse;
import cafe.jeffrey.profile.ai.chat.McpAnalysisAssistantService;
import cafe.jeffrey.profile.ai.chat.McpToolset;
import cafe.jeffrey.profile.ai.chat.McpToolsetFactory;
import cafe.jeffrey.profile.ai.chat.SuggestionRules;
import cafe.jeffrey.profile.ai.chat.SuggestionRules.QuestionRule;
import cafe.jeffrey.profile.ai.chat.SuggestionRules.ResponseRule;
import cafe.jeffrey.profile.ai.chat.ToolBinding;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Implementation of JFR Analysis Assistant using Spring AI with DuckDB tools for direct database access.
 */
public class JfrAnalysisAssistantServiceImpl extends McpAnalysisAssistantService implements JfrAnalysisAssistantService {

    private static final Logger LOG = LoggerFactory.getLogger(JfrAnalysisAssistantServiceImpl.class);

    private static final String ASSISTANT_NAME = "JFR Analysis Assistant (DuckDB tools)";

    private static final String AI_CALL_SPAN_NAME = "ai.jfr-analysis.call";

    private static final String FALLBACK_EVENTS_SCHEMA =
            "event_type VARCHAR, start_timestamp TIMESTAMPTZ, duration BIGINT (nanoseconds), "
                    + "samples BIGINT, weight BIGINT, weight_entity VARCHAR, "
                    + "stacktrace_hash BIGINT, thread_hash BIGINT, fields JSON";

    private static final Map<String, String> COLUMN_ANNOTATIONS = Map.of(
            "duration", " (nanoseconds)"
    );

    private static final SuggestionRules SUGGESTION_RULES = new SuggestionRules(
            List.of(
                    new QuestionRule(Set.of("cpu", "execution"), List.of(
                            "Show me the hottest methods by sample count",
                            "Which threads consumed the most CPU time?")),
                    new QuestionRule(Set.of("memory", "allocation"), List.of(
                            "What are the top allocation sites?",
                            "Show me allocation patterns over time")),
                    new QuestionRule(Set.of("gc", "garbage"), List.of(
                            "What is the average GC pause time?",
                            "Show me GC pause distribution"))),
            List.of(
                    new ResponseRule("thread", "thread", List.of(
                            "Show me thread activity breakdown"))),
            List.of(
                    "What are the main performance bottlenecks?",
                    "Show me a summary of all event types",
                    "Analyze CPU usage patterns"));

    private final DatabaseManagerResolver databaseManagerResolver;
    private final McpToolsetFactory mcpToolsetFactory;

    public JfrAnalysisAssistantServiceImpl(
            AiChatBackend chatBackend,
            DatabaseManagerResolver databaseManagerResolver,
            McpToolsetFactory mcpToolsetFactory) {
        super(ASSISTANT_NAME, chatBackend, SUGGESTION_RULES);
        this.databaseManagerResolver = databaseManagerResolver;
        this.mcpToolsetFactory = mcpToolsetFactory;
    }

    @Override
    public AssistantResponse analyze(ProfileInfo profileInfo, JfrAnalysisRequest request) {
        return runAnalysis(request.message(), () -> {
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

            String contextualMessage = buildContextualMessage(request, profileInfo);
            return new ToolExchange(
                    systemPrompt, request.history(), contextualMessage, toolBinding, AI_CALL_SPAN_NAME);
        });
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
                    return FALLBACK_EVENTS_SCHEMA;
                }
                return schema;
            }
        } catch (SQLException e) {
            LOG.warn("Failed to query events table schema, falling back to static schema: message={}", e.getMessage());
            return FALLBACK_EVENTS_SCHEMA;
        }
    }
}
