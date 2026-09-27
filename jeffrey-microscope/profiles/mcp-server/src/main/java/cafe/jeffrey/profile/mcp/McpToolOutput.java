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

import cafe.jeffrey.microscope.mcp.protocol.McpText;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;

/**
 * Jeffrey's ceiling on what one tool hands back to the model, and the capping helpers that apply it.
 * <p>
 * Silent truncation is the failure mode this exists to prevent: a capped list looks exactly like a
 * complete one, and a model that cannot see the cap reports the visible part as the whole story. Every
 * result that hit the ceiling therefore says so. A text answer ends with a line naming the cap
 * ({@link McpText}, the protocol's). A structured answer is not cut here at all: it bounds its own lists
 * before it is written and declares the cut in its record ({@code truncated}, an {@code omitted…} count,
 * {@code hasMore}), which is the one convention the server's instructions describe; the dispatcher
 * refuses one that still serialises past {@link #MAX_CHARS}.
 */
public final class McpToolOutput {

    /**
     * The most any single tool result may carry. Sized well under the point where a client spills the
     * result to a file, so a normal answer stays inline and readable. The dispatcher is given it as its
     * result limit, so the envelope applies it to every result whatever the tool did.
     */
    public static final int MAX_CHARS = 120_000;

    private McpToolOutput() {
    }

    /**
     * Caps a rendered result at {@link #MAX_CHARS}, appending an explicit note when anything was dropped.
     */
    public static String capped(String text) {
        return McpText.capped(text, MAX_CHARS);
    }

    /**
     * Caps a rendered body so that it, with the note announcing the cut, fits within {@code limit}:
     * for a body that something is appended to afterwards, such as a footer, which then still fits
     * under {@link #MAX_CHARS} when the envelope caps the whole text again.
     *
     * @throws IllegalArgumentException when the limit cannot hold even the note
     */
    public static String cappedWithin(String text, int limit) {
        return McpText.cappedWithin(text, limit);
    }

    /** A tool failure that the MCP envelope must mark with {@code isError=true}. */
    public static String error(String message) {
        throw new ToolExecutionException(message);
    }
}
