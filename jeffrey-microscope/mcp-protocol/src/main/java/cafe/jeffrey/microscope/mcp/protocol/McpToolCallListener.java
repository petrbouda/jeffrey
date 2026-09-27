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
 * Told about every {@code tools/call} of an advertised tool once it has been answered — the hook a
 * server's per-tool metrics hang on. A call naming a tool that is not advertised is not reported.
 * <p>
 * Called on the request thread, after the result is rendered: an implementation keeps it cheap and
 * does not throw.
 */
@FunctionalInterface
public interface McpToolCallListener {

    /** Counts nothing. */
    McpToolCallListener NONE = call -> {
    };

    void called(McpToolCall call);
}
