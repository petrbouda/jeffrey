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

package pbouda.jeffrey.profile.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import pbouda.jeffrey.profile.ai.config.AiAssistantProperties;
import pbouda.jeffrey.profile.ai.model.*;
import pbouda.jeffrey.profile.ai.prompt.OqlSystemPrompt;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of OQL Assistant Service using Spring AI ChatClient.
 */
public class OqlAssistantServiceImpl implements OqlAssistantService {

    private static final Logger LOG = LoggerFactory.getLogger(OqlAssistantServiceImpl.class);

    private final ChatClient chatClient;
    private final AiAssistantProperties properties;
    private final HeapDumpContextExtractor contextExtractor;
    private final OqlExtractor oqlExtractor;

    public OqlAssistantServiceImpl(
            ChatClient chatClient,
            AiAssistantProperties properties) {
        this.chatClient = chatClient;
        this.properties = properties;
        this.contextExtractor = new HeapDumpContextExtractor();
        this.oqlExtractor = new OqlExtractor();
        LOG.info("OQL Assistant initialized: provider={}", properties.provider());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public AiStatusResponse getStatus() {
        return new AiStatusResponse(
                properties.enabled(),
                properties.provider(),
                isAvailable()
        );
    }

    @Override
    public OqlChatResponse chat(HeapDumpContext context, OqlChatRequest request) {
        if (!isAvailable()) {
            return OqlChatResponse.textOnly("AI assistant is not configured. Please check your settings.");
        }

        try {
            // Format heap context for the prompt
            String contextText = contextExtractor.formatForPrompt(context);

            // Build conversation messages
            List<Message> messages = buildMessages(request, contextText);

            // Call the AI
            String response = chatClient.prompt()
                    .messages(messages)
                    .call()
                    .content();

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

    private List<Message> buildMessages(OqlChatRequest request, String contextText) {
        List<Message> messages = new ArrayList<>();

        // Add conversation history
        if (request.history() != null) {
            for (ChatMessage msg : request.history()) {
                if (msg.isUser()) {
                    messages.add(new UserMessage(msg.content()));
                } else {
                    // Include OQL in assistant message if present
                    String content = msg.oql() != null
                            ? msg.content() + "\n```sql\n" + msg.oql() + "\n```"
                            : msg.content();
                    messages.add(new AssistantMessage(content));
                }
            }
        }

        // Add current user message with heap context
        String userMessageWithContext = """
                ## Current Heap Dump Context
                %s

                ## User Request
                %s
                """.formatted(contextText, request.message());

        messages.add(new UserMessage(userMessageWithContext));

        return messages;
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
