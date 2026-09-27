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

/**
 * A {@code tools/call} that could not be dispatched to its tool: an argument is missing, does not fit
 * its type, or is not one of the values the schema allows — or, as {@link UnknownToolException}, the
 * name is not one this server advertises.
 * <p>
 * The MCP specification asks for input-validation failures to come back as tool results with
 * {@code isError} set rather than as protocol errors, because the model is the one who can correct the
 * call, and many clients never show it a JSON-RPC error. So the envelope answers this type inside the
 * result, with a message naming the argument and what it expects. Only an unknown tool — where there
 * is no tool whose result could carry the answer — and arguments that are not an object at all stay
 * {@code -32602}.
 * <p>
 * Extends {@link IllegalArgumentException} so that every existing caller — the ones that catch the
 * standard type to refuse a bad argument — keeps behaving as it did.
 */
public class ToolDispatchException extends IllegalArgumentException {

    private static final String MISSING_ARGUMENT = "Missing required argument '%s'. Expected: %s";
    private static final String INVALID_ARGUMENT = "Invalid argument '%s': %s";

    public ToolDispatchException(String message) {
        super(message);
    }

    /**
     * @param expected what the argument is for — its schema description, so the model can supply it
     */
    public static ToolDispatchException missingArgument(String name, String expected) {
        return new ToolDispatchException(MISSING_ARGUMENT.formatted(name, expected));
    }

    public static ToolDispatchException invalidArgument(String name, String reason) {
        return new ToolDispatchException(INVALID_ARGUMENT.formatted(name, reason));
    }
}
