/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
