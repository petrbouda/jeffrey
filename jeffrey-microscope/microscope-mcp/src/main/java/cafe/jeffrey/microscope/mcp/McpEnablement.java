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

/**
 * Whether the external MCP server answers right now.
 * <p>
 * Asked on every request rather than at wiring time, so a toggle takes effect without a restart. The
 * full Microscope answers from its settings; the MCP-only artifact has no reason to exist with the
 * server off, so it answers {@link #ALWAYS}.
 */
@FunctionalInterface
public interface McpEnablement {

    McpEnablement ALWAYS = () -> true;

    boolean enabled();
}
