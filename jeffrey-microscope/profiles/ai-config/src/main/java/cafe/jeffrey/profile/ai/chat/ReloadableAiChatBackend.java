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
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsChangeListener;
import cafe.jeffrey.shared.common.config.SettingsStore;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The single {@link AiChatBackend} every assistant depends on, delegating to whichever provider is
 * currently configured.
 * <p>
 * Assistants hold this object for the lifetime of the application, so changing the AI provider is a
 * matter of swapping what it points at rather than rebuilding the beans that use it.
 * <p>
 * Each method reads the delegate once into a local. A call that has already started therefore
 * completes against the backend it began with, even if the provider is switched underneath it — which
 * is the behaviour that makes a swap safe without any locking or cancellation.
 */
public final class ReloadableAiChatBackend implements AiChatBackend, SettingsChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(ReloadableAiChatBackend.class);

    private static final Set<String> OBSERVED = Set.of(
            MicroscopeSettingKeys.AI_PROVIDER,
            MicroscopeSettingKeys.AI_MODEL,
            MicroscopeSettingKeys.AI_MAX_TOKENS,
            MicroscopeSettingKeys.AI_API_KEY,
            MicroscopeSettingKeys.AI_BASE_URL,
            MicroscopeSettingKeys.AI_CLI_PATH,
            MicroscopeSettingKeys.AI_TIMEOUT_SECONDS);

    private final Function<SettingsStore, AiChatBackend> backendBuilder;
    private final Consumer<AiChatBackend> replacedBackendCleanup;

    private volatile AiChatBackend delegate;

    /**
     * @param backendBuilder         builds a backend from the current settings
     * @param replacedBackendCleanup disposes of a backend that has just been replaced; it must defer
     *                               the actual close, because calls that resolved the old backend may
     *                               still be running against it
     * @param initial                the backend to start with
     */
    public ReloadableAiChatBackend(
            Function<SettingsStore, AiChatBackend> backendBuilder,
            Consumer<AiChatBackend> replacedBackendCleanup,
            AiChatBackend initial) {

        this.backendBuilder = backendBuilder;
        this.replacedBackendCleanup = replacedBackendCleanup;
        this.delegate = initial;
    }

    @Override
    public Set<String> observedSettings() {
        return OBSERVED;
    }

    @Override
    public void onChanged(SettingsStore store) {
        // Built before the swap so a failure — a rejected API key, an unreachable endpoint — leaves the
        // previous, working backend in place instead of taking AI down.
        AiChatBackend rebuilt = backendBuilder.apply(store);

        AiChatBackend previous = delegate;
        delegate = rebuilt;

        LOG.info("AI backend reloaded: provider={} model={} available={}",
                rebuilt.providerName(), rebuilt.modelName(), rebuilt.isAvailable());

        if (previous != rebuilt) {
            replacedBackendCleanup.accept(previous);
        }
    }

    @Override
    public boolean isAvailable() {
        return delegate.isAvailable();
    }

    @Override
    public String providerName() {
        return delegate.providerName();
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public String chat(ChatExchange exchange, String spanName) {
        return delegate.chat(exchange, spanName);
    }

    @Override
    public ToolCallResult analyze(ToolExchange exchange) {
        return delegate.analyze(exchange);
    }
}
