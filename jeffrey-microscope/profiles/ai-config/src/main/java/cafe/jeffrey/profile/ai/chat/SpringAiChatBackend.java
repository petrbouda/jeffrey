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

import cafe.jeffrey.shared.common.span.Spans;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link AiChatBackend} implementation backed by Spring AI. Used for the API-key providers
 * (Claude via the Anthropic API, ChatGPT, Ollama). Tool calls are driven in-process via Spring AI's
 * {@code .tools(...)} mechanism using the {@link ToolBinding#springAiTools()} object.
 */
public final class SpringAiChatBackend implements AiChatBackend {

    private final ChatClient chatClient;
    private final String providerName;
    private final String modelName;

    public SpringAiChatBackend(ChatClient chatClient, String providerName, String modelName) {
        this.chatClient = chatClient;
        this.providerName = providerName;
        this.modelName = modelName;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public String chat(ChatExchange exchange, String spanName) {
        List<Message> messages = buildMessages(exchange.history(), exchange.userMessage());

        long span = Spans.start();
        try {
            ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
            if (exchange.systemPrompt() != null) {
                spec = spec.system(exchange.systemPrompt());
            }
            return spec.messages(messages)
                    .call()
                    .content();
        } finally {
            Spans.end(span, spanName);
        }
    }

    @Override
    public ToolCallResult analyze(ToolExchange exchange) {
        ToolCallingChatSession session = new ToolCallingChatSession(chatClient, exchange.spanName());
        String text = session.call(
                exchange.systemPrompt(),
                exchange.history(),
                exchange.userMessage(),
                exchange.toolBinding().springAiTools());
        // Spring AI does not surface invoked tool names through its high-level ChatClient API.
        return new ToolCallResult(text, List.of());
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
