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
 * A {@code resources/read} whose URI is well-formed and served by this endpoint, but whose subject —
 * the item the URI names, or the view of it — does not exist.
 *
 * <p>The envelope answers it with {@code -32602} and the exception's own sentence, so a client can tell
 * "that item is gone" from "the server broke" ({@code -32603}); {@code 2026-07-28} forbids the older
 * {@code -32002}. A provider may throw it directly; the dispatcher also derives it from a tool that failed
 * underneath a resource with an error its server names as not found ({@link McpFailurePolicy.Kind#NOT_FOUND}).</p>
 */
public class McpResourceNotFoundException extends RuntimeException {

    public McpResourceNotFoundException(String message) {
        super(message);
    }

    public McpResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
