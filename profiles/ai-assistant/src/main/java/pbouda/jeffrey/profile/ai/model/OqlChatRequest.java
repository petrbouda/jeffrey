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

import java.util.List;

/**
 * Request object for the OQL chat endpoint.
 *
 * @param message the user's message describing what they want to find
 * @param history previous messages in the conversation for context
 */
public record OqlChatRequest(
        String message,
        List<ChatMessage> history
) {
    /**
     * Create a request with no history.
     *
     * @param message the user's message
     * @return a new request with empty history
     */
    public static OqlChatRequest of(String message) {
        return new OqlChatRequest(message, List.of());
    }
}
