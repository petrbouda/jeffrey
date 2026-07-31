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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.ai.config.AiBackendFactory;
import cafe.jeffrey.profile.ai.config.AiBackendProvider;
import cafe.jeffrey.profile.ai.config.AiSettings;
import cafe.jeffrey.shared.common.config.SettingsStore;

import java.util.Optional;

/**
 * The single {@link AiChatBackend} every assistant depends on. It owns no client: it reads the AI
 * settings at the moment of a call, builds a backend for them, uses it, and closes it.
 * <p>
 * That is what makes a settings change take effect without a restart — not a reload, but the absence
 * of anything to reload. There is no cached client to invalidate and no window in which the
 * configuration and the client disagree.
 * <p>
 * The cost is one client construction per AI call. That is negligible beside a model round trip
 * measured in seconds, and the tool-calling loop inside a single {@link #analyze} shares one client,
 * so connections are still reused where it matters.
 * <p>
 * {@link #isAvailable()}, {@link #providerName()} and {@link #modelName()} deliberately build
 * nothing: they are asked on every profile page load.
 */
public final class SettingsDrivenAiChatBackend implements AiChatBackend {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsDrivenAiChatBackend.class);

    private static final String NOT_CONFIGURED = "AI is not configured";

    private final AiBackendFactory factory;
    private final SettingsStore settingsStore;

    public SettingsDrivenAiChatBackend(AiBackendFactory factory, SettingsStore settingsStore) {
        this.factory = factory;
        this.settingsStore = settingsStore;
    }

    @Override
    public boolean isAvailable() {
        AiSettings settings = currentSettings();
        return factory.find(settings)
                .map(provider -> provider.isAvailable(settings))
                .orElse(false);
    }

    @Override
    public String providerName() {
        // Null rather than a placeholder when AI is off: that is what the status endpoints report.
        return currentProvider().map(AiBackendProvider::displayName).orElse(null);
    }

    @Override
    public String modelName() {
        if (currentProvider().isEmpty()) {
            return null;
        }
        String model = currentSettings().model();
        return model.isBlank() ? null : model;
    }

    @Override
    public String chat(ChatExchange exchange, String spanName) {
        try (AiChatBackend backend = open()) {
            return backend.chat(exchange, spanName);
        }
    }

    @Override
    public ToolCallResult analyze(ToolExchange exchange) {
        try (AiChatBackend backend = open()) {
            return backend.analyze(exchange);
        }
    }

    /**
     * Builds the backend for the current settings. The caller closes it, so its lifetime is exactly
     * the call it serves.
     */
    private AiChatBackend open() {
        AiSettings settings = currentSettings();
        AiBackendProvider provider = factory.find(settings)
                .orElseThrow(() -> new IllegalStateException(NOT_CONFIGURED));

        LOG.debug("Opening AI backend: provider={} model={}", settings.provider(), settings.model());
        return provider.create(settings);
    }

    private Optional<AiBackendProvider> currentProvider() {
        return factory.find(currentSettings());
    }

    private AiSettings currentSettings() {
        return AiSettings.from(settingsStore);
    }
}
