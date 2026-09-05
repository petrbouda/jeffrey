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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The three things nearly every tool does to its arguments before it can use them: insist on the ones
 * it cannot work without, bound the ones that decide how much comes back, and cap a list before it is
 * rendered.
 * <p>
 * Each of these existed once per family — five copies of {@code boundedLimit} with three different
 * off-by-one conventions between them, and a {@code trim} written out separately for every element
 * type it was called with. That is a lot of surface for decisions that are the family's contract with
 * the model rather than anything specific to garbage collection or JDBC.
 * <p>
 * The refusal messages matter more here than the validation does. A model recovers from a bad call
 * only by reading what came back, so every message names the argument and the tool that produces a
 * good value for it, rather than reporting that something was wrong.
 */
final class ToolArguments {

    private ToolArguments() {
    }

    /**
     * An argument the tool genuinely cannot proceed without.
     * <p>
     * Worth stating even where the schema marks it required: a client is free to send the call
     * anyway, and a tool that reads past a missing selector answers a different question from the one
     * it was asked — which is worse than refusing, because nothing in the answer says so.
     *
     * @param recovery how to obtain a good value, e.g. the tool that lists them
     */
    static String required(String value, String argument, String recovery) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(argument + " is required. " + recovery);
        }
        return value.trim();
    }

    /**
     * A row cap the caller may lower but not raise: a model asking for everything is asking for its
     * own context to be spent on one answer.
     *
     * @param fallback what an omitted or nonsensical limit means
     * @param max      the most this tool will return however large the request
     */
    static int boundedLimit(Integer limit, int fallback, int max) {
        if (limit == null || limit < 1) {
            return fallback;
        }
        return Math.min(limit, max);
    }

    /**
     * The head of a list that has no bound of its own — endpoint and class counts are unbounded in
     * principle, since a service that puts identifiers in its paths produces one row per request.
     */
    static <T> List<T> firstOf(List<T> values, int max) {
        if (values.size() <= max) {
            return values;
        }
        // Copied rather than handed over as a subList view: the head outlives the rendering call, and
        // a view of a list somebody else still holds is a surprise waiting to happen. An ArrayList
        // copy rather than List.copyOf, which would additionally refuse a null element and turn a
        // rendering into a failure over something the row builder should answer for.
        return Collections.unmodifiableList(new ArrayList<>(values.subList(0, max)));
    }
}
