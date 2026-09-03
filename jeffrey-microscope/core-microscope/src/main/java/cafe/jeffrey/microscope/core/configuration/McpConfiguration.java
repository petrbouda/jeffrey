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

import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.microscope.mcp.McpEnablement;
import cafe.jeffrey.microscope.mcp.McpProfileResolver;
import cafe.jeffrey.microscope.mcp.McpServerConfiguration;
import cafe.jeffrey.microscope.mcp.SettingsMcpEnablement;
import cafe.jeffrey.shared.common.config.SettingsStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * The full Microscope's contribution to the external MCP server — the endpoint an interactive Claude
 * Code session connects to. The server itself is wired by {@link McpServerConfiguration}; this class
 * says how this application resolves a profile (through its workspace and hub managers, so remote
 * profiles are reachable) and when it answers (the opt-in setting, read per request).
 */
@Configuration
@Import(McpServerConfiguration.class)
public class McpConfiguration {

    @Bean
    public McpProfileResolver mcpProfileResolver(ProfileManagerResolver profileManagerResolver) {
        return profileManagerResolver::resolve;
    }

    @Bean
    public McpEnablement mcpEnablement(SettingsStore settingsStore) {
        return new SettingsMcpEnablement(settingsStore);
    }
}
