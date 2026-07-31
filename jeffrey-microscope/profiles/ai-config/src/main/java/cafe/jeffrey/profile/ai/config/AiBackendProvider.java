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
 * Builds the {@link AiChatBackend} for one provider.
 * <p>
 * Each provider contributes its own implementation as a bean, so {@link AiBackendFactory} can select
 * one by identifier instead of switching over a list of names it has to know about. This is what lets
 * the CLI-driven provider live in its own module — it is discovered, not referenced.
 */
public interface AiBackendProvider {

    /**
     * @return the value of {@code jeffrey.microscope.ai.provider} this implementation handles
     */
    String providerId();

    /**
     * @return the human-readable provider name reported by the AI status endpoints (e.g. "Claude")
     */
    String displayName();

    /**
     * Builds a backend for a single AI call. The caller closes it when the call ends, so an
     * implementation is free to allocate a connection pool here.
     */
    AiChatBackend create(AiSettings settings);

    /**
     * Whether this provider can currently serve a call.
     * <p>
     * Answered <em>without</em> building a backend: the feature list and the AI status endpoints ask
     * this on every profile page load, and constructing an SDK client to answer it would be far more
     * expensive than the question deserves. Providers reachable purely by API key are always
     * available; only a provider that shells out to a local binary has anything to check.
     */
    default boolean isAvailable(AiSettings settings) {
        return true;
    }
}
