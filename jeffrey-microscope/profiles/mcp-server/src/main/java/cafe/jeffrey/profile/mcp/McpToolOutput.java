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

package cafe.jeffrey.profile.mcp;

import cafe.jeffrey.shared.common.Json;

/**
 * Renders what a tool hands back to the model, and says so when it had to cut.
 * <p>
 * Silent truncation is the failure mode this exists to prevent: a capped list looks exactly like a
 * complete one, and a model that cannot see the cap reports the visible part as the whole story. Every
 * result that hit the ceiling therefore ends with a line naming the cap and what to do about it.
 */
public final class McpToolOutput {

    /**
     * The most any single tool result may carry. Sized well under the point where a client spills the
     * result to a file, so a normal answer stays inline and readable.
     */
    public static final int MAX_CHARS = 120_000;

    private static final String TRUNCATION_NOTE =
            "\n\n_TRUNCATED: the result exceeded %d characters and was cut here. "
                    + "Narrow the query — a smaller limit, a time range, or a more specific filter._";

    private static final String ERROR_PREFIX = "Error: ";

    private McpToolOutput() {
    }

    /**
     * Caps a rendered result, appending an explicit note when anything was dropped.
     */
    public static String capped(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CHARS) + TRUNCATION_NOTE.formatted(MAX_CHARS);
    }

    /**
     * A value rendered as JSON for the model, capped like any other result.
     */
    public static String json(Object value) {
        return capped(Json.toString(value));
    }

    /**
     * A domain answer of "there is nothing here", as opposed to a bad argument — which throws.
     */
    public static String error(String message) {
        return ERROR_PREFIX + message;
    }
}
