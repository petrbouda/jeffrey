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
