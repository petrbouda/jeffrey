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

package pbouda.jeffrey.profile.ai.mcp.model;

/**
 * A chat message in the JFR analysis conversation.
 *
 * @param role    the role (user or assistant)
 * @param content the message content
 */
public record JfrChatMessage(
        Role role,
        String content
) {
    public enum Role {
        USER,
        ASSISTANT
    }

    public boolean isUser() {
        return role == Role.USER;
    }

    public boolean isAssistant() {
        return role == Role.ASSISTANT;
    }

    public static JfrChatMessage user(String content) {
        return new JfrChatMessage(Role.USER, content);
    }

    public static JfrChatMessage assistant(String content) {
        return new JfrChatMessage(Role.ASSISTANT, content);
    }
}
