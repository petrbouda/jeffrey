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

import cafe.jeffrey.profile.mcp.McpToolOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * A catalogue answer written as a Markdown table, with a note under it saying how to read the columns
 * that need it.
 * <p>
 * The three catalogue tools — the profiles, the recordings, the hub sessions — answer with a table
 * rather than with JSON, because a reader scans these and a model picks one id out of them. Each of
 * the three had built its own by appending pipes to a {@link StringBuilder}, and each carried its own
 * copy of the one rule that actually matters: a value containing a pipe splits the cell it is in, so a
 * recording someone named {@code checkout | before} silently shifts every column after it and the ids
 * in that row stop being the ids the download tool takes.
 * <p>
 * Escaping is therefore not left to the caller. It happens on the way into a cell, once, here.
 */
final class MarkdownTable {

    private static final String CELL_SEPARATOR = " | ";
    private static final String ROW_PREFIX = "| ";
    private static final String ROW_SUFFIX = " |";
    private static final char PIPE = '|';
    private static final char PIPE_REPLACEMENT = '/';
    private static final char SPACE = ' ';
    private static final String HEADER_RULE_CELL = "---";

    private final StringBuilder out = new StringBuilder(1024);
    private final int columns;

    /**
     * What ends a line in a rendered table. Written out rather than taken from the platform: this text
     * is read by a model, not printed on the terminal of whoever is running Jeffrey, and a table whose
     * rows ended one way and whose notes ended another was rendering one document in two conventions.
     */
    private static final String LINE_BREAK = "\n";

    private MarkdownTable(List<String> headers) {
        this.columns = headers.size();
        appendRow(headers);
        appendRow(headers.stream().map(header -> HEADER_RULE_CELL).toList());
    }

    static MarkdownTable withColumns(String... headers) {
        return new MarkdownTable(List.of(headers));
    }

    /**
     * One row. Values are rendered with {@link String#valueOf}, so a caller passes what it has —
     * an {@code Instant}, a {@code long}, an enum — without spelling out the conversion; {@code null}
     * becomes an empty cell rather than the word "null".
     *
     * @throws IllegalArgumentException when the row does not match the header, which is a rendering
     *                                  mistake that would otherwise show up as a misaligned table
     */
    MarkdownTable row(Object... values) {
        if (values.length != columns) {
            throw new IllegalArgumentException(
                    "Row has " + values.length + " cells but the table has " + columns + " columns");
        }
        List<String> cells = new ArrayList<>(values.length);
        for (Object value : values) {
            cells.add(cell(value));
        }
        appendRow(cells);
        return this;
    }

    /**
     * A line under the table explaining a column that needs it — what an empty cell means, which tool
     * takes the id in it. These are the difference between a table a model can act on and one it has
     * to guess about.
     */
    MarkdownTable note(String note) {
        out.append(LINE_BREAK).append(note);
        if (!note.endsWith(LINE_BREAK)) {
            out.append(LINE_BREAK);
        }
        return this;
    }

    /**
     * The rendered table, capped like any other tool result.
     */
    String render() {
        return McpToolOutput.capped(out.toString());
    }

    /** Full table for callers that size complete pages before publishing them. */
    public String renderUncapped() {
        return out.toString();
    }

    private void appendRow(List<String> cells) {
        out.append(ROW_PREFIX)
                .append(String.join(CELL_SEPARATOR, cells))
                .append(ROW_SUFFIX)
                .append(LINE_BREAK);
    }

    /**
     * Keeps a value inside the cell it belongs to. A pipe would split the row; a newline or a carriage
     * return would end the table where it stands and leave the remaining rows as prose.
     */
    private static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return text.replace(PIPE, PIPE_REPLACEMENT)
                .replace('\n', SPACE)
                .replace('\r', SPACE);
    }
}
