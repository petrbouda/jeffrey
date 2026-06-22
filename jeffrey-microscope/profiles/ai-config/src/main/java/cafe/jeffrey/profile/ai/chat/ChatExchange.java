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
 * A prompt-only chat exchange: a system prompt, optional conversation history, and the current
 * user message. Used by assistants that do not expose tools to the model (e.g. the OQL assistant).
 *
 * @param systemPrompt the system prompt for this call (may be null to use the backend default)
 * @param history      optional conversation history (may be null)
 * @param userMessage  the current user message
 */
public record ChatExchange(
        String systemPrompt,
        List<ChatMessage> history,
        String userMessage
) {
}
