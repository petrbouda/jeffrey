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
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.Objects;

/**
 * The client's answer to one {@link McpInputRequest}, as it arrives in the retried request's
 * {@code inputResponses.<key>}.
 *
 * @param action  what the user did
 * @param content the form's values when accepted, otherwise usually null
 */
public record McpInputResponse(Action action, ObjectNode content) {

    private static final String FIELD_ACTION = "action";
    private static final String FIELD_CONTENT = "content";
    private static final String NOT_AN_OBJECT = "An input response must be an object";
    private static final String UNKNOWN_ACTION = "An input response's action must be one of accept, decline, cancel";
    private static final String CONTENT_NOT_AN_OBJECT = "An input response's content must be an object";

    public McpInputResponse {
        Objects.requireNonNull(action, "action");
        content = content == null ? null : content.deepCopy();
    }

    /**
     * @throws McpProtocolException {@code -32602} when the response is not an object, names no known
     *                              action, or carries content that is not an object
     */
    public static McpInputResponse parse(JsonNode response) {
        if (response == null || !response.isObject()) {
            throw new McpProtocolException(McpErrorCode.INVALID_PARAMS, NOT_AN_OBJECT);
        }
        Action action = Action.fromWire(response.path(FIELD_ACTION).asString(""));
        JsonNode content = response.get(FIELD_CONTENT);
        if (content != null && !content.isNull() && !content.isObject()) {
            throw new McpProtocolException(McpErrorCode.INVALID_PARAMS, CONTENT_NOT_AN_OBJECT);
        }
        return new McpInputResponse(action, content == null || content.isNull() ? null : (ObjectNode) content);
    }

    @Override
    public ObjectNode content() {
        return content == null ? null : content.deepCopy();
    }

    public enum Action {
        ACCEPT("accept"),
        DECLINE("decline"),
        CANCEL("cancel");

        private final String wireName;

        Action(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        /**
         * @throws McpProtocolException {@code -32602} for a name that is none of the three
         */
        public static Action fromWire(String wireName) {
            return Arrays.stream(values())
                    .filter(action -> action.wireName.equals(wireName))
                    .findFirst()
                    .orElseThrow(() -> new McpProtocolException(McpErrorCode.INVALID_PARAMS, UNKNOWN_ACTION));
        }
    }
}
