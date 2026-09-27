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
 * One {@code tools/call} of an advertised tool, as the dispatcher measured it: what a server's own
 * diagnostics count. Arguments and result contents are not part of it.
 *
 * @param tool          the tool's name
 * @param durationNanos how long the call took, rendering included
 * @param resultBytes   the size of the rendered result; zero when the call ended in an {@link Error}
 * @param isError       whether the result was an {@code isError} one, or there was none
 */
public record McpToolCall(String tool, long durationNanos, long resultBytes, boolean isError) {

    public McpToolCall {
        if (tool == null || tool.isBlank()) {
            throw new IllegalArgumentException("A tool call names its tool");
        }
        if (durationNanos < 0 || resultBytes < 0) {
            throw new IllegalArgumentException("A tool call's duration and size are not negative: durationNanos="
                    + durationNanos + " resultBytes=" + resultBytes);
        }
    }
}
