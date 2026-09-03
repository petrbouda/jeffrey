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

/**
 * The read-only MCP tool families, kept apart from the in-app AI modules so that an MCP server can
 * be built from the {@code @Tool} annotations alone, without a chat client or a model provider.
 */
module cafe.jeffrey.microscope.profile.mcp.tools {
    requires transitive cafe.jeffrey.microscope.profile.heapdump;
    requires cafe.jeffrey.shared.common;
    requires spring.ai.model;
    requires java.sql;
    requires org.slf4j;

    exports cafe.jeffrey.profile.mcp.tools.jfr;
    exports cafe.jeffrey.profile.mcp.tools.heapdump;
}
