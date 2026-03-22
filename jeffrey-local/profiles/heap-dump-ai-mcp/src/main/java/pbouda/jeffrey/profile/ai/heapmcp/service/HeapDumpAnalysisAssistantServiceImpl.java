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

package pbouda.jeffrey.profile.ai.heapmcp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import pbouda.jeffrey.profile.ai.heapmcp.model.HeapDumpAnalysisRequest;
import pbouda.jeffrey.profile.ai.heapmcp.model.HeapDumpAnalysisResponse;
import pbouda.jeffrey.profile.ai.heapmcp.model.HeapDumpChatMessage;
import pbouda.jeffrey.profile.ai.heapmcp.prompt.HeapDumpAnalysisSystemPrompt;
import pbouda.jeffrey.profile.ai.heapmcp.tools.HeapDumpMcpTools;
import pbouda.jeffrey.profile.ai.heapmcp.tools.HeapDumpToolsDelegate;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of heap dump analysis assistant using Spring AI with heap dump tools.
 */
public class HeapDumpAnalysisAssistantServiceImpl implements HeapDumpAnalysisAssistantService {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpAnalysisAssistantServiceImpl.class);

    private final ChatClient chatClient;
    private final String modelName;
    private final String providerName;

    public HeapDumpAnalysisAssistantServiceImpl(
            ChatClient.Builder chatClientBuilder,
            String modelName,
            String providerName) {
        this.chatClient = chatClientBuilder
                .defaultSystem(HeapDumpAnalysisSystemPrompt.SYSTEM_PROMPT)
                .build();
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
    public HeapDumpAnalysisResponse analyze(HeapDumpToolsDelegate delegate, HeapDumpAnalysisRequest request) {
        try {
            HeapDumpMcpTools tools = new HeapDumpMcpTools(delegate);

            List<Message> messages = buildMessages(request);

            List<String> toolsUsed = new ArrayList<>();
            String response = executeWithTools(messages, tools, toolsUsed);

            List<String> suggestions = generateSuggestions(request.message(), response);

            return new HeapDumpAnalysisResponse(response, suggestions, toolsUsed);
        } catch (Exception e) {
            LOG.error("Error during heap dump analysis: message={}", e.getMessage(), e);
            return HeapDumpAnalysisResponse.error("Analysis failed: " + e.getMessage());
        }
    }

    private List<Message> buildMessages(HeapDumpAnalysisRequest request) {
        List<Message> messages = new ArrayList<>();

        if (request.history() != null) {
            for (HeapDumpChatMessage msg : request.history()) {
                if (msg.isUser()) {
                    messages.add(new UserMessage(msg.content()));
                } else {
                    messages.add(new AssistantMessage(msg.content()));
                }
            }
        }

        messages.add(new UserMessage(request.message()));

        return messages;
    }

    private String executeWithTools(List<Message> messages, HeapDumpMcpTools tools, List<String> toolsUsed) {
        ChatResponse response = chatClient.prompt()
                .messages(messages)
                .tools(tools)
                .call()
                .chatResponse();

        return response.getResult().getOutput().getText();
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
