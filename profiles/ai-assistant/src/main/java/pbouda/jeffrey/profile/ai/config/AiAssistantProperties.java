/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the AI-powered OQL assistant.
 *
 * @param enabled  whether the AI assistant is enabled
 * @param provider the AI provider to use (anthropic, openai, or none)
 */
@ConfigurationProperties(prefix = "jeffrey.ai")
public record AiAssistantProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("none") String provider
) {

    /**
     * Check if the AI assistant is properly configured and available.
     *
     * @return true if enabled and a valid provider is set
     */
    public boolean isConfigured() {
        return enabled && provider != null && !provider.equals("none");
    }

    /**
     * Check if Anthropic Claude is the selected provider.
     *
     * @return true if anthropic is selected
     */
    public boolean isAnthropic() {
        return "anthropic".equalsIgnoreCase(provider);
    }

    /**
     * Check if OpenAI is the selected provider.
     *
     * @return true if openai is selected
     */
    public boolean isOpenAi() {
        return "openai".equalsIgnoreCase(provider);
    }
}
