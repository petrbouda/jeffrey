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
import cafe.jeffrey.shared.common.Json;

import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * The largest page of a catalogue that fits the response size limit, found by rendering it.
 * <p>
 * A page is measured whole -- its text and its structured content together -- and never trimmed after
 * rendering, because the cursor it hands back must continue exactly past the rows the client received.
 */
final class FittingPage {

    private FittingPage() {
    }

    /**
     * Whether an answer whose text is its own JSON fits the response size limit: the text and the
     * structured content are the same document, so measuring one measures both.
     */
    static boolean fits(Record answer) {
        return Json.toString(answer).length() <= McpToolOutput.MAX_CHARS;
    }

    /**
     * @param rows   how many rows the caller asked for, at most
     * @param render the page of the first {@code n} rows
     * @param fits   whether a rendered page is within the limit
     * @return the page of {@code rows} rows when it fits, else of the most rows that do; empty when not
     *         even one row fits
     */
    static <P> Optional<P> largest(int rows, IntFunction<P> render, Predicate<P> fits) {
        P whole = render.apply(rows);
        if (fits.test(whole)) {
            return Optional.of(whole);
        }
        int low = 1;
        int high = rows - 1;
        P fitting = null;
        while (low <= high) {
            int count = low + (high - low) / 2;
            P candidate = render.apply(count);
            if (fits.test(candidate)) {
                fitting = candidate;
                low = count + 1;
            } else {
                high = count - 1;
            }
        }
        return Optional.ofNullable(fitting);
    }
}
