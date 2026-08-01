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
 * Raw outcome of a tool-enabled model call: the assistant text and the names of any tools the model
 * invoked. Domain services enrich this into an {@link AssistantResponse} (adding follow-up
 * suggestions).
 *
 * @param text      the assistant's response text
 * @param toolsUsed the names of tools the model invoked during the call (never null)
 */
public record ToolCallResult(
        String text,
        List<String> toolsUsed
) {
    public ToolCallResult {
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
    }
}
