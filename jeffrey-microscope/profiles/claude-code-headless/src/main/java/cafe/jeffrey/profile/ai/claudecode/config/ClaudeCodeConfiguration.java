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

import org.springframework.context.annotation.Bean;
import cafe.jeffrey.profile.ai.config.AiBackendProvider;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;

/**
 * Contributes the {@code claude-code} provider — a backend driving the Claude Code CLI in headless
 * mode, authenticated through the host's Claude subscription rather than an API key.
 * <p>
 * The provider is always registered. Whether it is actually used is decided at call time by the
 * configured {@code jeffrey.microscope.ai.provider}, so switching to or away from Claude Code no longer
 * needs a restart.
 */
public class ClaudeCodeConfiguration {

    private static final String DEFAULT_CLI_PATH = "claude";

    /**
     * Detects whether the CLI is installed. Declared here rather than in the application module
     * because this module owns the class, and the provider bean below depends on it.
     * <p>
     * The CLI path is supplied rather than fixed, so editing it in the settings page takes effect
     * without a restart.
     */
    @Bean
    public ClaudeCodeDetector claudeCodeDetector(SettingsStore settingsStore) {
        return new ClaudeCodeDetector(
                () -> settingsStore.getString(MicroscopeSettingKeys.AI_CLI_PATH, DEFAULT_CLI_PATH));
    }

    @Bean
    public AiBackendProvider claudeCodeBackendProvider(ClaudeCodeDetector claudeCodeDetector) {
        return new ClaudeCodeBackendProvider(claudeCodeDetector);
    }
}
