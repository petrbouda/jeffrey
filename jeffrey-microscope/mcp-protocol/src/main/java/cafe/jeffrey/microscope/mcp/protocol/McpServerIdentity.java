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

package cafe.jeffrey.microscope.mcp.protocol;

import tools.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * The server's name and version, as {@code server/discover} and every other result state it in
 * {@code _meta["io.modelcontextprotocol/serverInfo"]} — one object, so no two results can disagree.
 * The server that adapts this protocol supplies both.
 */
public record McpServerIdentity(String name, String version) {

    private static final String FIELD_NAME = "name";
    private static final String FIELD_VERSION = "version";

    public McpServerIdentity {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A server identity needs a name");
        }
        Objects.requireNonNull(version, "version");
    }

    /** A fresh {@code Implementation} object: the caller may place it in its own result. */
    public ObjectNode toJson() {
        return McpJson.createObject().put(FIELD_NAME, name).put(FIELD_VERSION, version);
    }
}
