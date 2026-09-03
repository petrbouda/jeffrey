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

package cafe.jeffrey.microscope.mcp.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;

/**
 * Tells the operator where the server is and how to point Claude Code at it — the one line this
 * artifact exists to print, since there is no Settings page to copy the URL from.
 */
public class McpStartedListener implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(McpStartedListener.class);

    private static final String ENDPOINT_PATH = "/api/internal/mcp";
    private static final String SERVER_NAME = "jeffrey";

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        String url = "http://localhost:" + event.getWebServer().getPort() + ENDPOINT_PATH;
        LOG.info("Microscope MCP started: url={}", url);
        LOG.info("Connect Claude Code with: claude mcp add --transport http {} {}", SERVER_NAME, url);
    }
}
