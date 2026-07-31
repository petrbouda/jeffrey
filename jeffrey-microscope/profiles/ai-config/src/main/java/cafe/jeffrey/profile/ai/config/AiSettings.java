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

import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;

import java.time.Duration;
import java.util.Locale;

/**
 * The AI configuration a backend is built from, read as one consistent snapshot.
 * <p>
 * Taking a snapshot matters: the settings are written field by field, and building a backend from
 * values read at different moments could pair a newly chosen provider with the previous provider's
 * API key.
 *
 * @param provider the selected provider identifier, lower-cased
 * @param model    the model name, empty when the provider chooses it implicitly
 * @param maxTokens the output token budget
 * @param apiKey   the API key, empty for providers that do not use one
 * @param baseUrl  the endpoint for self-hosted providers
 * @param cliPath  the executable used by CLI-driven providers
 * @param timeout  the per-invocation timeout for CLI-driven providers
 */
public record AiSettings(
        String provider,
        String model,
        int maxTokens,
        String apiKey,
        String baseUrl,
        String cliPath,
        Duration timeout) {

    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final int DEFAULT_TIMEOUT_SECONDS = 600;
    private static final String DEFAULT_CLI_PATH = "claude";

    public AiSettings {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("AI provider must not be blank");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("AI timeout must be positive: " + timeout);
        }
    }

    public static AiSettings from(SettingsStore store) {
        String provider = store
                .getString(MicroscopeSettingKeys.AI_PROVIDER, MicroscopeSettingKeys.PROVIDER_NONE)
                .trim()
                .toLowerCase(Locale.ROOT);

        return new AiSettings(
                provider.isEmpty() ? MicroscopeSettingKeys.PROVIDER_NONE : provider,
                store.getString(MicroscopeSettingKeys.AI_MODEL, ""),
                store.getInt(MicroscopeSettingKeys.AI_MAX_TOKENS, DEFAULT_MAX_TOKENS),
                store.getString(MicroscopeSettingKeys.AI_API_KEY, ""),
                store.getString(MicroscopeSettingKeys.AI_BASE_URL, SpringAiBackendFactory.DEFAULT_OLLAMA_BASE_URL),
                store.getString(MicroscopeSettingKeys.AI_CLI_PATH, DEFAULT_CLI_PATH),
                Duration.ofSeconds(store.getInt(MicroscopeSettingKeys.AI_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS)));
    }
}
