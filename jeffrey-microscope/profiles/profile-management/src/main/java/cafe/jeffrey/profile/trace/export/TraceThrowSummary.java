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

package cafe.jeffrey.profile.trace.export;

import cafe.jeffrey.profile.manager.model.trace.TraceExceptionRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Every throw recorded inside a trace, grouped by what was thrown.
 *
 * @param groups  the distinct throws, escaping ones first and then by how often each happened
 * @param total   how many throws there were in all
 * @param escaped how many of those failed their span
 */
record TraceThrowSummary(List<TraceThrowGroup> groups, long total, long escaped) {

    /** What makes two throws the same finding. The message is part of it: one class thrown for two
     *  different reasons is two findings, and the message is usually the only thing that says so. */
    private record Key(String thrownClass, String message, String eventType) {
    }

    private static final String UNKNOWN_SPAN = "<unknown span>";

    static final TraceThrowSummary EMPTY = new TraceThrowSummary(List.of(), 0, 0);

    /**
     * @param exceptions the trace's throws, each already attributed to a span
     * @param spans      the trace's spans, so a throw can be reported against a name rather than
     *                   against a hex id no reader can resolve
     */
    static TraceThrowSummary of(List<TraceExceptionRow> exceptions, List<TraceSpanRow> spans) {
        if (exceptions.isEmpty()) {
            return EMPTY;
        }

        Map<String, String> spanNames = spans.stream()
                .collect(Collectors.toMap(
                        TraceSpanRow::spanId,
                        TraceSpanRow::name,
                        (first, duplicate) -> first));

        Map<Key, List<TraceExceptionRow>> grouped = exceptions.stream()
                .collect(Collectors.groupingBy(
                        row -> new Key(row.thrownClass(), row.message(), row.eventType()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        // Escaping throws first: they are the ones that failed something. Everything else is ranked
        // by volume, which is what makes an exception-as-control-flow group visible at a glance.
        List<TraceThrowGroup> groups = grouped.entrySet().stream()
                .map(entry -> toGroup(entry.getKey(), entry.getValue(), spanNames))
                .sorted(Comparator.comparing(TraceThrowGroup::hasEscaped).reversed()
                        .thenComparing(Comparator.comparingLong(TraceThrowGroup::count).reversed()))
                .toList();

        return new TraceThrowSummary(
                groups,
                exceptions.size(),
                exceptions.stream().filter(TraceExceptionRow::escaped).count());
    }

    private static TraceThrowGroup toGroup(
            Key key, List<TraceExceptionRow> rows, Map<String, String> spanNames) {

        List<TraceThrowGroup.Site> sites = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> spanNames.getOrDefault(row.spanId(), UNKNOWN_SPAN),
                        LinkedHashMap::new,
                        Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new TraceThrowGroup.Site(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(TraceThrowGroup.Site::count).reversed()
                        .thenComparing(TraceThrowGroup.Site::spanName))
                .toList();

        return new TraceThrowGroup(
                key.thrownClass(),
                key.message(),
                key.eventType(),
                rows.size(),
                rows.stream().filter(TraceExceptionRow::escaped).count(),
                sites);
    }

    boolean isEmpty() {
        return groups.isEmpty();
    }
}
