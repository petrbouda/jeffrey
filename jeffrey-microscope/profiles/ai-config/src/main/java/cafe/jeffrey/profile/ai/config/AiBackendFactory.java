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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.DisabledAiChatBackend;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Selects the {@link AiBackendProvider} matching the configured provider and builds a backend from it.
 * <p>
 * An unknown or absent provider yields a {@link DisabledAiChatBackend} rather than an error: AI is an
 * optional feature, and "not configured" is the normal state for a fresh installation.
 */
public class AiBackendFactory {

    private static final Logger LOG = LoggerFactory.getLogger(AiBackendFactory.class);

    private final Map<String, AiBackendProvider> providersById;

    public AiBackendFactory(List<AiBackendProvider> providers) {
        this.providersById = providers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        provider -> provider.providerId().toLowerCase(Locale.ROOT), Function.identity()));
    }

    /**
     * @return a backend for the configured provider, or a disabled one when AI is off or unrecognised
     */
    public AiChatBackend create(AiSettings settings) {
        AiBackendProvider provider = providersById.get(settings.provider());
        if (provider == null) {
            LOG.info("AI is not enabled: provider={} known_providers={}",
                    settings.provider(), providersById.keySet());
            return new DisabledAiChatBackend();
        }

        return provider.create(settings);
    }
}
