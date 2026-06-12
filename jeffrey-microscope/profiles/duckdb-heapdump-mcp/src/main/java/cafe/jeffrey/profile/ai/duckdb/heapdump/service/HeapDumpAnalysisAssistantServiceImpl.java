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

package cafe.jeffrey.profile.ai.duckdb.heapdump.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import cafe.jeffrey.profile.ai.chat.AssistantResponse;
import cafe.jeffrey.profile.ai.chat.ToolCallingChatSession;
import cafe.jeffrey.profile.ai.duckdb.heapdump.model.HeapDumpAnalysisRequest;
import cafe.jeffrey.profile.ai.duckdb.heapdump.prompt.HeapDumpAnalysisSystemPrompt;
import cafe.jeffrey.profile.ai.duckdb.heapdump.tools.HeapDumpMcpTools;
import cafe.jeffrey.profile.ai.duckdb.heapdump.tools.HeapDumpToolsDelegate;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of heap dump analysis assistant using Spring AI with heap dump tools.
 */
public class HeapDumpAnalysisAssistantServiceImpl implements HeapDumpAnalysisAssistantService {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpAnalysisAssistantServiceImpl.class);

    private static final String AI_CALL_SPAN_NAME = "ai.heapdump-analysis.call";

    private final ToolCallingChatSession chatSession;
    private final String modelName;
    private final String providerName;

    public HeapDumpAnalysisAssistantServiceImpl(
            ChatClient.Builder chatClientBuilder,
            String modelName,
            String providerName) {
        ChatClient chatClient = chatClientBuilder
                .defaultSystem(HeapDumpAnalysisSystemPrompt.SYSTEM_PROMPT)
                .build();
        this.chatSession = new ToolCallingChatSession(chatClient, AI_CALL_SPAN_NAME);
        this.modelName = modelName;
        this.providerName = providerName;
        LOG.info("Heap Dump Analysis Assistant initialized: provider={} model={}", providerName, modelName);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public AssistantResponse analyze(HeapDumpToolsDelegate delegate, HeapDumpAnalysisRequest request) {
        try {
            HeapDumpMcpTools tools = new HeapDumpMcpTools(delegate);

            String response = chatSession.call(request.history(), request.message(), tools);

            List<String> suggestions = generateSuggestions(request.message(), response);

            return new AssistantResponse(response, suggestions, List.of());
        } catch (Exception e) {
            LOG.error("Error during heap dump analysis: message={}", e.getMessage(), e);
            return AssistantResponse.error("Analysis failed: " + e.getMessage());
        }
    }

    private List<String> generateSuggestions(String question, String response) {
        List<String> suggestions = new ArrayList<>();
        String lowerQuestion = question.toLowerCase();
        String lowerResponse = response.toLowerCase();

        if (lowerQuestion.contains("summary") || lowerQuestion.contains("overview")) {
            suggestions.add("Show me the class histogram by size");
            suggestions.add("Are there any leak suspects?");
        }

        if (lowerQuestion.contains("histogram") || lowerQuestion.contains("class")) {
            suggestions.add("Show me the biggest objects by retained size");
            suggestions.add("Browse instances of the largest class");
        }

        if (lowerQuestion.contains("leak") || lowerQuestion.contains("suspect")) {
            suggestions.add("Show the dominator tree roots");
            suggestions.add("What are the biggest objects?");
        }

        if (lowerQuestion.contains("string")) {
            suggestions.add("Analyze collection fill ratios");
            suggestions.add("Show the class histogram");
        }

        if (lowerResponse.contains("retained") && !lowerQuestion.contains("dominator")) {
            suggestions.add("Show the dominator tree for more retained size details");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Show me the heap summary");
            suggestions.add("What are the top memory consumers?");
            suggestions.add("Are there potential memory leaks?");
        }

        return suggestions.stream().limit(3).toList();
    }
}
