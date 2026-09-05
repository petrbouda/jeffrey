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

import java.util.List;

/**
 * The resources one endpoint offers, and the templates for the ones whose URI carries an argument.
 */
public interface McpResourceProvider {

    /**
     * The resources that exist without anything being filled in.
     */
    List<McpResource> resources();

    /**
     * The templates, whose URIs carry {@code {placeholders}} a client substitutes — a profile id, an
     * event type. Listing every flamegraph of every profile as a concrete resource would be a list
     * nobody can read and most of which nobody wants.
     */
    List<McpResource> templates();

    /**
     * @return the contents of the resource at that URI
     * @throws IllegalArgumentException if the URI matches nothing this server serves
     */
    Contents read(String uri);

    /**
     * @param mimeType how to render {@code text}, from {@link McpResource}'s constants
     */
    record Contents(String uri, String mimeType, String text) {
    }
}
