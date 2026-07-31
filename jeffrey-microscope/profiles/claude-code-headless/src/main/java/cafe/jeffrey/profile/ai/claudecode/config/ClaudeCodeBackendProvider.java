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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.claudecode.ClaudeCodeChatBackend;
import cafe.jeffrey.profile.ai.claudecode.ClaudeCodeCliClient;
import cafe.jeffrey.profile.ai.config.AiBackendProvider;
import cafe.jeffrey.profile.ai.config.AiSettings;

/**
 * Claude Code in headless mode, driving the CLI on the host. Authentication reuses the host's Claude
 * subscription, so no API key is required.
 */
public final class ClaudeCodeBackendProvider implements AiBackendProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ClaudeCodeBackendProvider.class);

    private static final String PROVIDER_ID = "claude-code";
    private static final String CLI_DEFAULT_MODEL = "<cli-default>";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public AiChatBackend create(AiSettings settings) {
        LOG.info("Creating Claude Code backend: cli_path={} model={} timeout_in_sec={}",
                settings.cliPath(),
                settings.model().isBlank() ? CLI_DEFAULT_MODEL : settings.model(),
                settings.timeout().toSeconds());

        ClaudeCodeCliClient cliClient = new ClaudeCodeCliClient(settings.cliPath(), settings.timeout());
        return new ClaudeCodeChatBackend(cliClient, settings.model());
    }
}
