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

package pbouda.jeffrey.profile.ai.model;

/**
 * Represents a single message in the chat conversation.
 *
 * @param role    the role of the message sender ("user" or "assistant")
 * @param content the text content of the message
 * @param oql     optional OQL query extracted from assistant responses (nullable)
 */
public record ChatMessage(
        String role,
        String content,
        String oql
) {
    /**
     * Check if this is a user message.
     *
     * @return true if the role is "user"
     */
    public boolean isUser() {
        return "user".equalsIgnoreCase(role);
    }

    /**
     * Check if this is an assistant message.
     *
     * @return true if the role is "assistant"
     */
    public boolean isAssistant() {
        return "assistant".equalsIgnoreCase(role);
    }
}
