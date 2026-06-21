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

package cafe.jeffrey.profile.ai.oql.service;

import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.ChatExchange;
import cafe.jeffrey.profile.ai.chat.ChatMessage;
import cafe.jeffrey.profile.ai.oql.model.AiStatusResponse;
import cafe.jeffrey.profile.ai.oql.model.HeapDumpContext;
import cafe.jeffrey.profile.ai.oql.model.OqlChatRequest;
import cafe.jeffrey.profile.ai.oql.model.OqlChatResponse;
import cafe.jeffrey.profile.ai.oql.prompt.OqlSystemPrompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of OQL Assistant Service over the provider-agnostic {@link AiChatBackend}.
 */
public class OqlAssistantServiceImpl implements OqlAssistantService {

    private static final Logger LOG = LoggerFactory.getLogger(OqlAssistantServiceImpl.class);

    private static final String AI_CALL_SPAN_NAME = "ai.oql.call";

    private final AiChatBackend chatBackend;
    private final HeapDumpContextExtractor contextExtractor;
    private final OqlExtractor oqlExtractor;

    public OqlAssistantServiceImpl(AiChatBackend chatBackend) {
        this.chatBackend = chatBackend;
        this.contextExtractor = new HeapDumpContextExtractor();
        this.oqlExtractor = new OqlExtractor();
        LOG.info("OQL Assistant initialized: provider={}", chatBackend.providerName());
    }

    @Override
    public boolean isAvailable() {
        return chatBackend.isAvailable();
    }

    @Override
    public AiStatusResponse getStatus() {
        return new AiStatusResponse(true, chatBackend.providerName(), isAvailable());
    }

    @Override
    public OqlChatResponse chat(HeapDumpContext context, OqlChatRequest request) {
        if (!isAvailable()) {
            return OqlChatResponse.textOnly("AI assistant is not configured. Please check your settings.");
        }

        try {
            String contextText = contextExtractor.formatForPrompt(context);
            ChatExchange exchange = new ChatExchange(
                    OqlSystemPrompt.SYSTEM_PROMPT,
                    convertHistory(request),
                    buildUserMessage(request, contextText));

            String response = chatBackend.chat(exchange, AI_CALL_SPAN_NAME);

            // Extract OQL from response
            String extractedOql = oqlExtractor.extract(response);

            // Clean response text
            String cleanedContent = oqlExtractor.cleanResponse(response, extractedOql);

            // Generate follow-up suggestions
            List<String> suggestions = generateFollowupSuggestions(extractedOql);

            return new OqlChatResponse(cleanedContent, extractedOql, suggestions);

        } catch (Exception e) {
            LOG.error("Error calling AI: message={}", e.getMessage(), e);
            return OqlChatResponse.textOnly("Sorry, I encountered an error processing your request: " + e.getMessage());
        }
    }

    private List<ChatMessage> convertHistory(OqlChatRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        if (request.history() == null) {
            return messages;
        }
        for (var msg : request.history()) {
            if (msg.isUser()) {
                messages.add(ChatMessage.user(msg.content()));
            } else {
                // Include OQL in assistant message if present so the model sees its prior query.
                String content = msg.oql() != null
                        ? msg.content() + "\n```sql\n" + msg.oql() + "\n```"
                        : msg.content();
                messages.add(ChatMessage.assistant(content));
            }
        }
        return messages;
    }

    private String buildUserMessage(OqlChatRequest request, String contextText) {
        return """
                ## Current Heap Dump Context
                %s

                ## User Request
                %s
                """.formatted(contextText, request.message());
    }

    private List<String> generateFollowupSuggestions(String oql) {
        if (oql == null || oql.isBlank()) {
            return List.of();
        }

        List<String> suggestions = new ArrayList<>();
        String lowerOql = oql.toLowerCase();

        // Suggest referrers for string queries
        if (lowerOql.contains("java.lang.string")) {
            suggestions.add("Show what's holding these strings");
            suggestions.add("Find duplicate string values");
        }

        // Suggest analysis for collections
        if (lowerOql.contains("hashmap") || lowerOql.contains("arraylist") || lowerOql.contains("hashset")) {
            suggestions.add("Show entries with largest retained size");
            suggestions.add("Find what references these collections");
        }

        // Suggest retained size if not present
        if (!lowerOql.contains("rsizeof")) {
            suggestions.add("Add retained size to results");
        }

        // Suggest referrers if not present
        if (!lowerOql.contains("referrers")) {
            suggestions.add("Show what's holding these objects");
        }

        // Limit to 3 suggestions
        return suggestions.stream().limit(3).toList();
    }
}
