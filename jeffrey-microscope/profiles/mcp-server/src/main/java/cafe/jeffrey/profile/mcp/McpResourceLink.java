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
