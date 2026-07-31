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
     * Builds a backend from the current settings. Called again whenever the AI settings change, so an
     * implementation must not assume it is invoked only once.
     */
    AiChatBackend create(AiSettings settings);
}
