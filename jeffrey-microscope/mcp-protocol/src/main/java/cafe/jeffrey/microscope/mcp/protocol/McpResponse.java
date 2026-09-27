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

/**
 * What the dispatcher answers, before any transport has touched it: the HTTP status and the JSON body.
 * A notification is accepted with {@code 202} and no body at all.
 *
 * @param status the HTTP status
 * @param body   the JSON-RPC response, or null for none
 */
public record McpResponse(int status, JsonNode body) {

    private static final int ACCEPTED_STATUS = 202;
    private static final int OK_STATUS = 200;
    private static final int MIN_STATUS = 100;
    private static final int MAX_STATUS = 599;

    /** A notification: accepted, never answered. */
    public static final McpResponse ACCEPTED = new McpResponse(ACCEPTED_STATUS, null);

    public McpResponse {
        if (status < MIN_STATUS || status > MAX_STATUS) {
            throw new IllegalArgumentException("Not an HTTP status: " + status);
        }
    }

    static McpResponse ok(JsonNode body) {
        return new McpResponse(OK_STATUS, body);
    }

    /** Whether there is a body to send. */
    public boolean hasBody() {
        return body != null;
    }
}
