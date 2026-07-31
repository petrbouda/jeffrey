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

/**
 * A self-hosted model served by Ollama; no API key, but a configurable endpoint.
 */
public final class OllamaBackendProvider implements AiBackendProvider {

    private static final String PROVIDER_ID = "ollama";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public AiChatBackend create(AiSettings settings) {
        return SpringAiBackendFactory.ollama(settings.baseUrl(), settings.model(), settings.maxTokens());
    }
}
