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
import cafe.jeffrey.profile.ai.config.AiBackendProvider;
import cafe.jeffrey.profile.ai.config.AiSettings;

/**
 * Claude Code in headless mode, driving the CLI on the host. Authentication reuses the host's Claude
 * subscription, so no API key is required.
 */
public final class ClaudeCodeBackendProvider implements AiBackendProvider {

    private static final String PROVIDER_ID = "claude-code";
    private static final String DISPLAY_NAME = "Claude Code";

    private final ClaudeCodeDetector detector;

    /**
     * @param detector answers "is the CLI installed", memoized per CLI path. Availability is asked far
     *                 more often than the CLI is invoked — once per profile page load — so it must not
     *                 fork a process each time.
     */
    public ClaudeCodeBackendProvider(ClaudeCodeDetector detector) {
        this.detector = detector;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public boolean isAvailable(AiSettings settings) {
        return detector.isInstalled();
    }

    @Override
    public AiChatBackend create(AiSettings settings) {
        ClaudeCodeCliClient cliClient = new ClaudeCodeCliClient(settings.cliPath(), settings.timeout());
        return new ClaudeCodeChatBackend(cliClient, settings.model());
    }
}
