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

package cafe.jeffrey.microscope.core.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.profile.ai.claudecode.config.ClaudeCodeDetector;

/**
 * Always-on wiring for Claude Code detection. Unlike the AI provider beans (gated on the configured
 * provider at startup), the detector exists regardless so the UI can offer to enable Claude Code when it
 * is installed but no provider is configured.
 */
@Configuration
public class AiProviderDetectionConfiguration {

    @Bean
    public ClaudeCodeDetector claudeCodeDetector(
            @Value("${jeffrey.microscope.ai.cli-path:claude}") String cliPath) {
        return new ClaudeCodeDetector(cliPath);
    }
}
