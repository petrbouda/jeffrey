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

/**
 * The backend in use when no AI provider is configured.
 * <p>
 * AI used to be switched off by leaving its beans unregistered, which meant the choice was fixed for
 * the lifetime of the JVM. Representing "off" as a backend instead keeps the wiring identical in both
 * states, so the provider can be turned on and off while the application runs.
 * <p>
 * The provider and model names are {@code null} rather than a placeholder, because that is what the
 * status endpoints report when AI is unavailable.
 */
public final class DisabledAiChatBackend implements AiChatBackend {

    private static final String NOT_CONFIGURED = "AI is not configured";

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String providerName() {
        return null;
    }

    @Override
    public String modelName() {
        return null;
    }

    @Override
    public String chat(ChatExchange exchange, String spanName) {
        throw new IllegalStateException(NOT_CONFIGURED);
    }

    @Override
    public ToolCallResult analyze(ToolExchange exchange) {
        throw new IllegalStateException(NOT_CONFIGURED);
    }
}
