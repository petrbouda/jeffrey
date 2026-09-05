/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
        out.append(System.lineSeparator()).append(note);
        if (!note.endsWith(System.lineSeparator())) {
            out.append(System.lineSeparator());
        }
        return this;
    }

    /**
     * The rendered table, capped like any other tool result.
     */
    String render() {
        return McpToolOutput.capped(out.toString());
    }

    private void appendRow(List<String> cells) {
        out.append(ROW_PREFIX)
                .append(String.join(CELL_SEPARATOR, cells))
                .append(ROW_SUFFIX)
                .append('\n');
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
