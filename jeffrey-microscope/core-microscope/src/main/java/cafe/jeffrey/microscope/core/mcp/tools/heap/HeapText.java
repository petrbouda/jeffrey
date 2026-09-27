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


package cafe.jeffrey.microscope.core.mcp.tools.heap;

/**
 * Pieces the fixed-width heap answers share.
 */
final class HeapText {

    /** What a cell reads when the value is unknown. */
    static final String NONE = "-";

    private static final String ELLIPSIS = "...";
    private static final String RULE = "-";

    private HeapText() {
    }

    /** The value cut to a column's width, with an ellipsis where it was cut; empty for null. */
    static String cut(String value, int width) {
        if (value == null) {
            return "";
        }
        if (value.length() <= width) {
            return value;
        }
        return value.substring(0, width - ELLIPSIS.length()) + ELLIPSIS;
    }

    /**
     * The line that states a list's cut: how many rows it left out, or that more may exist when that
     * count is unknown. Nothing when the list is whole.
     */
    static StringBuilder omitted(StringBuilder text, Integer omitted, String rows) {
        if (omitted == null) {
            return text.append("\n(more ").append(rows).append(" may exist beyond these)\n");
        }
        if (omitted > 0) {
            return text.append("\n(").append(omitted).append(" more ").append(rows).append(" not listed)\n");
        }
        return text;
    }

    /** The rule under a table header, ending its line. */
    static String rule(int width) {
        return RULE.repeat(width) + "\n";
    }
}
