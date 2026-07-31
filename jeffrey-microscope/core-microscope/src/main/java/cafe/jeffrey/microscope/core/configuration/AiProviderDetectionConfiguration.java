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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.profile.ai.claudecode.config.ClaudeCodeDetector;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;

/**
 * Wiring for Claude Code detection. The detector exists regardless of the configured provider so the UI
 * can offer to enable Claude Code when it is installed but unused.
 * <p>
 * The CLI path is passed as a supplier rather than a value, so editing it in the settings page takes
 * effect without a restart.
 */
@Configuration
public class AiProviderDetectionConfiguration {

    private static final String DEFAULT_CLI_PATH = "claude";

    @Bean
    public ClaudeCodeDetector claudeCodeDetector(SettingsStore settingsStore) {
        return new ClaudeCodeDetector(
                () -> settingsStore.getString(MicroscopeSettingKeys.AI_CLI_PATH, DEFAULT_CLI_PATH));
    }
}
