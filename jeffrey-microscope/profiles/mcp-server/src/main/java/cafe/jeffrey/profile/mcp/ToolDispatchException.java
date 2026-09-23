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

/**
 * A {@code tools/call} that could not be dispatched to a tool at all: the name is not one this server
 * advertises, or the arguments do not fit the tool's schema.
 * <p>
 * The distinction this type carries is the one the MCP specification draws between a protocol error and
 * a tool error. A tool that ran and failed — no heap dump on this profile, a query that found nothing —
 * answers the model inside the result, with {@code isError} set, because the model is expected to read
 * it and try something else. A call that never reached a tool is not an answer to anything: the client
 * asked for something this server does not offer, and it is told so through the JSON-RPC error channel
 * as {@code -32602}, where a client can tell it apart from a failed analysis.
 * <p>
 * Extends {@link IllegalArgumentException} so that every existing caller — the ones that catch the
 * standard type to refuse a bad argument — keeps behaving as it did.
 */
public class ToolDispatchException extends IllegalArgumentException {

    public ToolDispatchException(String message) {
        super(message);
    }
}
