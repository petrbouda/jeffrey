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
 * A tool-enabled chat exchange: a system prompt, optional history, the current user message, and a
 * {@link ToolBinding} giving the model access to a profile's analysis tools.
 *
 * @param systemPrompt the system prompt for this call (may be null to use the backend default)
 * @param history      optional conversation history (may be null)
 * @param userMessage  the current user message
 * @param toolBinding  the tool access binding (provider-agnostic)
 * @param spanName     the measurement span name recorded for the AI call
 */
public record ToolExchange(
        String systemPrompt,
        List<ChatMessage> history,
        String userMessage,
        ToolBinding toolBinding,
        String spanName
) {
}
