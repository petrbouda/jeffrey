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

import cafe.jeffrey.microscope.mcp.protocol.McpCursor;
import cafe.jeffrey.profile.mcp.JeffreyMcpServer;

/**
 * Cursor paging over an engine that pages by offset - the heap index, the trace operations and the
 * attribute search. The tools hand out an opaque {@link McpCursor} bound to the filters of the call, so
 * a cursor cannot be replayed against another class, object, query or condition.
 */
public final class OffsetPaging {

    private OffsetPaging() {
    }

    /**
     * @return where the page starts: the cursor's offset, or the first row without one
     * @throws IllegalArgumentException when the cursor is malformed or belongs to other filters
     */
    public static int offset(String cursor, McpCursor.Filters filters) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        return JeffreyMcpServer.CURSOR.decodeOffset(cursor.trim(), filters);
    }

    /**
     * Whether the page continues, and the cursor that reads on from the last row it showed.
     *
     * @param offset where this page started
     * @param shown  the rows this answer carries, which may be fewer than the engine returned
     * @param more   whether rows remain after the ones shown
     */
    public static McpCursor.Next next(McpCursor.Filters filters, int offset, int shown, boolean more) {
        if (!more || shown == 0) {
            return McpCursor.Next.END;
        }
        return new McpCursor.Next(true, JeffreyMcpServer.CURSOR.encode(filters, new McpCursor.Offset(offset + shown)));
    }
}
