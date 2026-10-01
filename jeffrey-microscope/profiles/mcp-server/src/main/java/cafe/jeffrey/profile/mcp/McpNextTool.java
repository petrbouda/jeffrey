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

import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpJsonObject;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * A call the reader can make next, ready to send: the tool, the arguments it takes as they stand —
 * real ids, epoch-millisecond times, a cursor — and one short clause saying what it answers.
 * <p>
 * It routes and never diagnoses, like the prose it replaces: {@code why} says what the call shows,
 * not that anything above is wrong. Built with {@link #call}, so a tool names its arguments with
 * typed values instead of assembling a JSON object by hand.
 *
 * @param tool      an advertised tool name, {@code family_name}
 * @param arguments the arguments to pass unchanged
 * @param why       what the call answers, in one short clause
 * @param weight    what the call's answer puts into the conversation, read from the tool's own hints
 */
public record McpNextTool(
        @McpDescription("An advertised tool name")
        String tool,
        @McpDescription("Arguments to pass unchanged")
        McpJsonObject arguments,
        @McpDescription("What the call answers, in one short clause")
        String why,
        @McpDescription("What the call's answer puts into the conversation: LIGHT a few compact records, "
                + "MEDIUM a dashboard or ranking, HEAVY a long document such as a flamegraph or trace")
        McpToolWeight weight) {

    public McpNextTool {
        if (tool == null || tool.isBlank()) {
            throw new IllegalArgumentException("tool must not be blank: tool=" + tool);
        }
        if (arguments == null) {
            throw new IllegalArgumentException("arguments must not be null: tool=" + tool);
        }
        if (why == null || why.isBlank()) {
            throw new IllegalArgumentException("why must not be blank: tool=" + tool);
        }
        if (weight == null) {
            throw new IllegalArgumentException("weight must not be null: tool=" + tool);
        }
    }

    /**
     * The start of a call to the given tool, with the weight its own hints imply (an
     * {@link McpToolWeights} reads it); add its arguments with {@link Call#with} and finish it with
     * {@link Call#why}.
     */
    public static Call call(String tool, McpToolWeight weight) {
        return new Call(tool, weight);
    }

    /**
     * The arguments of one call, in the order they are given. A {@code null} string or enum is left
     * out: an absent optional argument is how the tool reads its own default.
     */
    public static final class Call {

        private final String tool;
        private final McpToolWeight weight;
        private final ObjectNode arguments = Json.createObject();

        private Call(String tool, McpToolWeight weight) {
            this.tool = tool;
            this.weight = weight;
        }

        public Call with(String name, String value) {
            if (value != null) {
                named(name).put(name, value);
            }
            return this;
        }

        public Call with(String name, long value) {
            named(name).put(name, value);
            return this;
        }

        /**
         * A finite number; NaN and the infinities have no JSON spelling and are refused.
         */
        public Call with(String name, double value) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "argument must be finite: tool=" + tool + " argument=" + name + " value=" + value);
            }
            named(name).put(name, value);
            return this;
        }

        public Call with(String name, boolean value) {
            named(name).put(name, value);
            return this;
        }

        /**
         * A list of strings, such as the ids of the files to bring; {@code null} is left out like an
         * absent string, and an empty list is written as one.
         */
        public Call with(String name, List<String> values) {
            if (values != null) {
                ArrayNode array = named(name).putArray(name);
                values.forEach(array::add);
            }
            return this;
        }

        public Call with(String name, Enum<?> value) {
            if (value != null) {
                named(name).put(name, value.name());
            }
            return this;
        }

        /**
         * The finished call, with what it answers.
         */
        public McpNextTool why(String why) {
            return new McpNextTool(tool, new McpJsonObject(arguments), why, weight);
        }

        private ObjectNode named(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("argument name must not be blank: tool=" + tool);
            }
            if (arguments.has(name)) {
                throw new IllegalArgumentException("argument given twice: tool=" + tool + " argument=" + name);
            }
            return arguments;
        }
    }
}
