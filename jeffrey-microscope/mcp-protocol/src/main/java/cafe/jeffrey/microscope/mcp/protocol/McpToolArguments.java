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
 * The shape every {@code tools/call} argument list must have before any one value is read.
 */
public final class McpToolArguments {

    private static final String NOT_AN_OBJECT = "Tool arguments must be an object";

    private McpToolArguments() {
    }

    /**
     * Absent arguments read as none; present ones must be a JSON object.
     *
     * @throws ToolDispatchException when they are anything else — a malformed call, not a mistake in one
     *                               value, which the dispatcher answers {@code -32602} before dispatch
     */
    public static void requireObject(JsonNode arguments) {
        if (arguments != null && !arguments.isObject()) {
            throw new ToolDispatchException(NOT_AN_OBJECT);
        }
    }
}
