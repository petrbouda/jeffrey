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

/**
 * Provider-agnostic seam over the configured AI backend. Implementations wrap either Spring AI
 * (API-key providers: Claude, ChatGPT, Ollama) or the Claude Code CLI in headless mode (subscription
 * authentication). Domain assistants depend on this interface so the same feature code works
 * regardless of which provider is selected.
 */
public interface AiChatBackend {

    /**
     * @return true if the backend is reachable and properly configured
     */
    boolean isAvailable();

    /**
     * @return the display name of the provider (e.g. "Claude", "Claude Code")
     */
    String providerName();

    /**
     * @return the model name in use, or null when the provider chooses it implicitly
     */
    String modelName();

    /**
     * Execute a prompt-only chat call (no tools).
     *
     * @param exchange the system prompt, history, and user message
     * @param spanName the measurement span name recorded for the call
     * @return the assistant's response text
     */
    String chat(ChatExchange exchange, String spanName);

    /**
     * Execute a tool-enabled call, giving the model access to a profile's analysis tools.
     *
     * @param exchange the prompt, history, message, and tool binding
     * @return the assistant text plus the names of any tools invoked
     */
    ToolCallResult analyze(ToolExchange exchange);
}
