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

import cafe.jeffrey.profile.manager.ProfileManager;

/**
 * Finds the {@link ProfileManager} of a profile an MCP client asked about.
 * <p>
 * The one seam between the MCP server and the application hosting it. The full Microscope resolves
 * through its workspace and hub managers, so a profile that lives in a remote workspace is reachable;
 * the MCP-only artifact resolves straight from the core database, because that is all it has.
 */
@FunctionalInterface
public interface McpProfileResolver {

    /**
     * @throws cafe.jeffrey.shared.common.exception.JeffreyClientException when no such profile exists
     */
    ProfileManager resolve(String profileId);
}
