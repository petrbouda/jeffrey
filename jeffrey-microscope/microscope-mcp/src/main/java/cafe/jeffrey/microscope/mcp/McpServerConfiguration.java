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

package cafe.jeffrey.microscope.mcp;

import cafe.jeffrey.microscope.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wiring of the external MCP server that is the same wherever it is hosted.
 * <p>
 * The hosting application contributes two beans: a {@link McpProfileResolver} saying how a profile id
 * becomes a {@code ProfileManager}, and a {@link McpEnablement} saying whether the server answers.
 * The {@code ExternalMcpController} itself is a scanned {@code @RestController}, so the host also lists
 * {@code cafe.jeffrey.microscope.mcp} among its scanned packages.
 */
@Configuration
public class McpServerConfiguration {

    /**
     * Holds each profile an MCP client is working on open between its questions, and lets go once the
     * client has stopped asking. Closed with the context so the pinned pools are released on shutdown.
     */
    @Bean(destroyMethod = "close")
    public McpProfileContextCache mcpProfileContextCache(
            McpProfileResolver profileResolver,
            DatabaseManagerResolver databaseManagerResolver,
            Clock applicationClock) {
        return new McpProfileContextCache(profileResolver, databaseManagerResolver, applicationClock);
    }

    @Bean
    public ProfilesMcpTools profilesMcpTools(MicroscopeCorePersistenceProvider localCorePersistenceProvider) {
        return new ProfilesMcpTools(localCorePersistenceProvider.localCoreRepositories());
    }

    @Bean
    public McpToolsetAssembler mcpToolsetAssembler(
            ProfilesMcpTools profilesMcpTools,
            McpProfileContextCache contextCache,
            JfrFlamegraphPanelProvider panelProvider) {
        return new McpToolsetAssembler(profilesMcpTools, contextCache, panelProvider);
    }
}
