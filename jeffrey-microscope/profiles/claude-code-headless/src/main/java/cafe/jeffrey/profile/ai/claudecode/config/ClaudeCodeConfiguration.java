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

package cafe.jeffrey.profile.ai.claudecode.config;

import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.claudecode.ClaudeCodeChatBackend;
import cafe.jeffrey.profile.ai.claudecode.ClaudeCodeCliClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * Wires the {@code claude-code} provider: a provider-agnostic {@link AiChatBackend} backed by the
 * Claude Code CLI in headless mode. Authentication reuses the host's Claude subscription, so no API
 * key is required.
 * <p>
 * Active only when {@code jeffrey.microscope.ai.provider} is {@code claude-code}; the API-key
 * providers (Claude via the Anthropic API, ChatGPT, Ollama) are wired separately by
 * {@code AiChatModelConfiguration}. Database-stored settings are injected into the Spring Environment
 * on startup by {@code SettingsConfiguration}, so they are available via {@code @Value} and
 * {@code @ConditionalOnExpression}.
 */
@ConditionalOnExpression("'${jeffrey.microscope.ai.provider:none}' == 'claude-code'")
public class ClaudeCodeConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ClaudeCodeConfiguration.class);

    private static final String CLI_DEFAULT_MODEL = "<cli-default>";

    @Bean
    public AiChatBackend aiChatBackend(
            @Value("${jeffrey.microscope.ai.model:}") String modelName,
            @Value("${jeffrey.microscope.ai.cli-path:claude}") String cliPath,
            @Value("${jeffrey.microscope.ai.timeout-seconds:120}") int timeoutSeconds) {

        LOG.info("Creating Claude Code backend: cli_path={} model={} timeout_in_sec={}",
                cliPath, modelName.isBlank() ? CLI_DEFAULT_MODEL : modelName, timeoutSeconds);
        ClaudeCodeCliClient cliClient = new ClaudeCodeCliClient(cliPath, Duration.ofSeconds(timeoutSeconds));
        return new ClaudeCodeChatBackend(cliClient, modelName);
    }
}
