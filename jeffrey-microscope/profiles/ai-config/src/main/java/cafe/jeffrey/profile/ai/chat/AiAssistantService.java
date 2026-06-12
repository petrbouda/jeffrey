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
 * Common contract of an AI analysis assistant: availability and provider/model identification.
 * Domain-specific assistants extend this interface with their own {@code analyze} method.
 */
public interface AiAssistantService {

    /**
     * Check if the AI assistant is available and properly configured.
     *
     * @return true if the assistant is available
     */
    boolean isAvailable();

    /**
     * Get the name of the AI model being used.
     *
     * @return the model name, or null if not configured
     */
    String getModelName();

    /**
     * Get the display name of the AI provider (e.g. "Claude", "ChatGPT").
     *
     * @return the provider display name, or null if not configured
     */
    String getProviderName();
}
