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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.mcp.protocol.McpDescription;

/**
 * A time window on the UTC epoch-millisecond base every MCP time travels in: what a windowed answer
 * says it covered, and what a next call passes straight back.
 *
 * @param startEpochMs where the window starts, inclusive
 * @param endEpochMs   where it ends, after its start
 */
record EpochWindow(
        @McpDescription("Start of the window, as UTC epoch milliseconds")
        long startEpochMs,
        @McpDescription("End of the window, as UTC epoch milliseconds")
        long endEpochMs) {

    EpochWindow {
        if (endEpochMs <= startEpochMs) {
            throw new IllegalArgumentException("endEpochMs must be greater than startEpochMs: startEpochMs="
                    + startEpochMs + " endEpochMs=" + endEpochMs);
        }
    }
}
