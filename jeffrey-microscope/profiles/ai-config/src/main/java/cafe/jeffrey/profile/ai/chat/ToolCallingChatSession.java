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

package cafe.jeffrey.profile.ai.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import cafe.jeffrey.shared.common.span.Spans;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable Spring AI tool-calling chat session: converts the conversation history into
 * Spring AI messages, appends the current user message, and executes a single tool-enabled
 * call wrapped in a measurement span.
 */
public final class ToolCallingChatSession {

    private final ChatClient chatClient;
    private final String spanName;

    /**
     * @param chatClient the Spring AI chat client to execute calls with
     * @param spanName   the span name recorded for each AI call (e.g. "ai.jfr-analysis.call")
     */
    public ToolCallingChatSession(ChatClient chatClient, String spanName) {
        this.chatClient = chatClient;
        this.spanName = spanName;
    }

    /**
     * Execute a tool-enabled chat call without a per-call system prompt
     * (e.g. when the system prompt is configured as the client's default).
     *
     * @param history     optional conversation history (may be null)
     * @param userMessage the current user message
     * @param tools       the tool-annotated object exposed to the model
     * @return the assistant's response text
     */
    public String call(List<ChatMessage> history, String userMessage, Object tools) {
        return call(null, history, userMessage, tools);
    }

    /**
     * Execute a tool-enabled chat call with an explicit per-call system prompt.
     *
     * @param systemPrompt the system prompt for this call, or null to use the client's default
     * @param history      optional conversation history (may be null)
     * @param userMessage  the current user message
     * @param tools        the tool-annotated object exposed to the model
     * @return the assistant's response text
     */
    public String call(String systemPrompt, List<ChatMessage> history, String userMessage, Object tools) {
        List<Message> messages = buildMessages(history, userMessage);

        long aiSpan = Spans.start();
        ChatResponse response;
        try {
            ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
            if (systemPrompt != null) {
                spec = spec.system(systemPrompt);
            }
            response = spec
                    .messages(messages)
                    .tools(tools)
                    .call()
                    .chatResponse();
        } finally {
            Spans.end(aiSpan, spanName);
        }

        return response.getResult().getOutput().getText();
    }

    private static List<Message> buildMessages(List<ChatMessage> history, String userMessage) {
        List<Message> messages = new ArrayList<>();

        if (history != null) {
            for (ChatMessage msg : history) {
                if (msg.isUser()) {
                    messages.add(new UserMessage(msg.content()));
                } else {
                    messages.add(new AssistantMessage(msg.content()));
                }
            }
        }

        messages.add(new UserMessage(userMessage));

        return messages;
    }
}
