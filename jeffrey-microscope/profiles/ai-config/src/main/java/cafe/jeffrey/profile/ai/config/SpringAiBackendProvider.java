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

package cafe.jeffrey.profile.ai.config;

import cafe.jeffrey.profile.ai.chat.AiChatBackend;

import java.util.function.Function;

/**
 * A provider backed by Spring AI. The three of them (Claude, ChatGPT, Ollama) differ only in which
 * {@link SpringAiBackendFactory} method they call, so they are one type with three instances rather
 * than three near-identical classes; the mapping is then visible in one place in
 * {@link AiChatModelConfiguration}.
 *
 * @param providerId  the value of {@code jeffrey.microscope.ai.provider} this instance handles
 * @param displayName the name reported by the AI status endpoints
 * @param builder     turns the current settings into a backend
 */
public record SpringAiBackendProvider(
        String providerId,
        String displayName,
        Function<AiSettings, AiChatBackend> builder) implements AiBackendProvider {

    public SpringAiBackendProvider {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Provider id must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name must not be blank: providerId=" + providerId);
        }
        if (builder == null) {
            throw new IllegalArgumentException("Builder must not be null: providerId=" + providerId);
        }
    }

    @Override
    public AiChatBackend create(AiSettings settings) {
        return builder.apply(settings);
    }
}
