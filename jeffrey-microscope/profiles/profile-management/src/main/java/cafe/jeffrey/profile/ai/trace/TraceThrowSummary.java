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

package cafe.jeffrey.profile.ai.trace;

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
