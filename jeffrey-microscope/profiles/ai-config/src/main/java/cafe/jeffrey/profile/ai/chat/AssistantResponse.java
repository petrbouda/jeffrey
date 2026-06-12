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

import java.util.List;

/**
 * Response from an AI analysis assistant.
 *
 * @param content     the analysis response text
 * @param suggestions optional follow-up suggestions for the user
 * @param toolsUsed   list of MCP tools that were invoked during analysis
 */
public record AssistantResponse(
        String content,
        List<String> suggestions,
        List<String> toolsUsed
) {
    public static AssistantResponse textOnly(String content) {
        return new AssistantResponse(content, List.of(), List.of());
    }

    public static AssistantResponse error(String errorMessage) {
        return new AssistantResponse(errorMessage, List.of(), List.of());
    }
}
