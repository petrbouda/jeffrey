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
package cafe.jeffrey.profile.mcp;

import java.util.Objects;

/**
 * A pointer, returned beside a tool's answer, to the resource that holds the same thing.
 * <p>
 * A tool result scrolls away; a resource a client has attached stays in view and can be referred back
 * to. Several of Jeffrey's tools have an exact resource counterpart already, so the link costs nothing
 * to produce and lets a client keep the answer rather than re-ask for it.
 *
 * @param uri         the resource URI, which must be one {@code resources/read} actually serves
 * @param name        a short name for the link
 * @param description what the resource holds
 * @param mimeType    the media type {@code resources/read} answers with
 */
public record McpResourceLink(String uri, String name, String description, String mimeType) {

    public McpResourceLink {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(name, "name");
    }
}
