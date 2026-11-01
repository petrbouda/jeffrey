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
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;

/**
 * Jeffrey's ceiling on what one tool hands back to the model, and the capping helpers that apply it.
 * <p>
 * Silent truncation is the failure mode this exists to prevent: a capped list looks exactly like a
 * complete one, and a model that cannot see the cap reports the visible part as the whole story. Every
 * result that hit the ceiling therefore says so. A text answer ends with a line naming the cap
 * ({@link McpText}, the protocol's). A structured answer bounds its own lists before it is written and
 * declares the cut in its record ({@code truncated}, an {@code omitted…} count, {@code hasMore}), which
 * is the one convention the server's instructions describe; a Markdown document it carries is cut by
 * {@link #withDocument}, which hands the record the flag. The dispatcher refuses one that still
 * serialises past {@link #MAX_CHARS}.
 */
public final class McpToolOutput {

    /**
     * The most any single tool result may carry. Sized well under the point where a client spills the
     * result to a file, so a normal answer stays inline and readable. The dispatcher is given it as its
     * result limit, so the envelope applies it to every result whatever the tool did.
     */
    public static final int MAX_CHARS = 120_000;

    /**
     * The most an answer of a tool that declares no {@code maxResultSizeChars} may carry. A client
     * applies its own default limit to such a tool — Claude Code's is 25,000 tokens, past which it
     * saves the result to a file and hands the agent an error instead (measured: 42,000 characters of
     * JSON inline; 75,000 and 120,000 saved) — so these answers are sized well under it, leaving room
     * for JSON dense in tokens. A tool whose answer is a long document declares {@link #MAX_CHARS}
     * instead, and with it the client's larger allowance.
     */
    public static final int UNDECLARED_MAX_CHARS = 50_000;

    private static final String EMPTY_TEXT = "";

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

    /**
     * A record that carries a Markdown document, the document cut so the whole record serialises within
     * {@link #MAX_CHARS}. The document goes into the record, not only into the text, because a host may
     * hand the model only the structured content when there is one - Claude Code does - and a record
     * that merely counted the document's characters would reach that model as metadata without the
     * answer. JSON escaping lengthens the document, and unevenly, so the cut is measured on the
     * serialised record rather than on the document's own length: each pass keeps the share of the
     * document that the room left for it allows, and repeats while the record is still over.
     *
     * @param document the Markdown answer, whole; null is empty
     * @param record   builds the record from the document it carries and whether that was cut
     * @throws IllegalArgumentException when the record's other components alone leave no room for the
     *                                  note announcing the cut
     */
    public static <R extends Record> R withDocument(String document, DocumentRecord<R> record) {
        String whole = document == null ? EMPTY_TEXT : document;
        R built = record.of(whole, false);
        int chars = structuredChars(built);
        if (chars <= MAX_CHARS) {
            return built;
        }
        int others = structuredChars(record.of(EMPTY_TEXT, true));
        long room = MAX_CHARS - others;
        String carried = whole;
        while (chars > MAX_CHARS) {
            long documentChars = chars - others;
            int limit = (int) (carried.length() * room / documentChars);
            carried = McpText.cappedWithin(whole, limit);
            built = record.of(carried, true);
            chars = structuredChars(built);
        }
        return built;
    }

    private static int structuredChars(Record record) {
        return McpToolResult.of(EMPTY_TEXT, record).structuredChars();
    }

    /** A record built around the Markdown document it carries, for {@link #withDocument}. */
    @FunctionalInterface
    public interface DocumentRecord<R extends Record> {

        /**
         * @param document the document as the record carries it, possibly cut
         * @param cut      whether it was cut to fit {@link #MAX_CHARS}
         */
        R of(String document, boolean cut);
    }

    /** A tool failure that the MCP envelope must mark with {@code isError=true}. */
    public static String error(String message) {
        throw new ToolExecutionException(message);
    }
}
