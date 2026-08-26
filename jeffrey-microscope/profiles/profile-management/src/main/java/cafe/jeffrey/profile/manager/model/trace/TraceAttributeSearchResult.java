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

package cafe.jeffrey.profile.manager.model.trace;

import cafe.jeffrey.provider.profile.api.TraceAttributeCarrier;

import java.util.List;

/**
 * The traces an attribute search matched.
 *
 * @param matches       the page's traces, each with the spans that satisfied a condition
 * @param totalMatching how many traces matched in total, so a capped page can say it is one
 * @param stats         the whole match summarised — which the capped page cannot be summed into
 */
public record TraceAttributeSearchResult(
        List<Match> matches,
        long totalMatching,
        Stats stats) {

    /**
     * One matched trace, with what matched it.
     *
     * @param trace the trace itself, exactly as the trace list would show it
     * @param hits  the carriers that satisfied a condition, capped: a trace where four hundred spans
     *              carry the value is answered by the first few, and the rest would bury the row
     */
    public record Match(TraceRow trace, List<Hit> hits) {
    }

    /**
     * One carrier that satisfied a condition, and what it held. This is what keeps the answer in the
     * list — without it, finding out which span carried the value means opening the trace.
     *
     * @param carrier whether a span or a notification matched, so the row can say which it was
     *                rather than calling a notification a span
     * @param spanId  the span, as a hex string for the same reason a trace id is one; {@code null}
     *                when a notification matched outside any span, which is an answer rather than a
     *                missing value
     */
    public record Hit(TraceAttributeCarrier carrier, String spanId, String key, String value) {
    }

    /** The matched traces summarised, aggregated over every match rather than over the page. */
    public record Stats(
            long traces,
            long tracesWithErrors,
            long totalNanos,
            long p50Nanos,
            long p95Nanos,
            long maxNanos) {
    }
}
