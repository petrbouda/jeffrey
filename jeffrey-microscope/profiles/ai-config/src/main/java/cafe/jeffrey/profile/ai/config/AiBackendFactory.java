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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Looks up the {@link AiBackendProvider} matching the configured provider.
 * <p>
 * An unknown or absent provider resolves to empty rather than an error: AI is optional, and "not
 * configured" is the normal state for a fresh installation.
 */
public class AiBackendFactory {

    private final Map<String, AiBackendProvider> providersById;

    public AiBackendFactory(List<AiBackendProvider> providers) {
        this.providersById = providers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        provider -> provider.providerId().toLowerCase(Locale.ROOT), Function.identity()));
    }

    /**
     * @return the provider for these settings, or empty when AI is off or the provider is unrecognised
     */
    public Optional<AiBackendProvider> find(AiSettings settings) {
        return Optional.ofNullable(providersById.get(settings.provider()));
    }

    /**
     * @return every provider identifier that can be configured; used for diagnostics
     */
    public Set<String> knownProviders() {
        return providersById.keySet();
    }
}
