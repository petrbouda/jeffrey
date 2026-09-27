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

import tools.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * A request the protocol layer refuses, carrying the code it is answered with and, when the code
 * defines one, the {@code error.data} object — the supported versions after {@code -32022}, the
 * missing capability after {@code -32021}.
 */
public class McpProtocolException extends RuntimeException {

    private final McpErrorCode code;
    private final JsonNode data;

    public McpProtocolException(McpErrorCode code, String message) {
        this(code, message, null);
    }

    public McpProtocolException(McpErrorCode code, String message, JsonNode data) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.data = data == null ? null : data.deepCopy();
    }

    public McpErrorCode code() {
        return code;
    }

    /** The {@code error.data} to send, or null when the code defines none. */
    public JsonNode data() {
        return data == null ? null : data.deepCopy();
    }
}
