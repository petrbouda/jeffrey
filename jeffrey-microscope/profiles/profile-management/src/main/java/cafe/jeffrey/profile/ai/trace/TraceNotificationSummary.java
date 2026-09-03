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

import cafe.jeffrey.profile.manager.model.trace.TraceNotificationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Everything the application said while a trace ran, grouped by what it said.
 *
 * @param groups the distinct notifications, the most severe first and then by how often each was raised
 * @param total  how many notifications there were in all
 * @param urgent how many of those were {@code CRITICAL} or {@code HIGH} — the ones that are a finding
 *               on their own rather than context for one
 */
record TraceNotificationSummary(List<TraceNotificationGroup> groups, long total, long urgent) {

    /**
     * What makes two notifications the same finding. The message is part of it: Jeffrey's own
     * emitter keeps it constant per type, but a third-party emitter may not, and a type that carried
     * two different sentences is two findings.
     */
    private record Key(String type, String severity, String message) {
    }

    /**
     * The severities that make a notification a finding in its own right. Everything below them is
     * context — worth reading beside a slow span, not worth reporting on its own.
     */
    static final Set<String> URGENT_SEVERITIES = Set.of("CRITICAL", "HIGH");

    /** Where a severity sorts; an unrecognised one goes last rather than failing the export. */
    private static final Map<String, Integer> SEVERITY_RANK = Map.of(
            "CRITICAL", 0,
            "HIGH", 1,
            "MEDIUM", 2,
            "LOW", 3);
    private static final int UNRANKED_SEVERITY = SEVERITY_RANK.size();

    /** A notification the trace holds but no span does: none was open, or the one it named is absent. */
    static final String NO_SPAN = "outside any span";
    private static final String UNKNOWN_SPAN = "<unknown span>";

    static final TraceNotificationSummary EMPTY = new TraceNotificationSummary(List.of(), 0, 0);

    /**
     * @param notifications the trace's notifications, oldest first
     * @param spans         the trace's spans, so a notification can be reported against a name rather
     *                      than against a hex id no reader can resolve
     * @param traceStartMs  when the trace started, in milliseconds from the recording's beginning, so
     *                      an occurrence can be placed relative to the trace rather than the recording
     */
    static TraceNotificationSummary of(
            List<TraceNotificationRow> notifications, List<TraceSpanRow> spans, long traceStartMs) {

        if (notifications.isEmpty()) {
            return EMPTY;
        }

        Map<String, String> spanNames = spans.stream()
                .collect(Collectors.toMap(
                        TraceSpanRow::spanId,
                        TraceSpanRow::name,
                        (first, duplicate) -> first));

        Map<Key, List<TraceNotificationRow>> grouped = notifications.stream()
                .collect(Collectors.groupingBy(
                        row -> new Key(row.type(), row.severity(), row.message()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<TraceNotificationGroup> groups = grouped.entrySet().stream()
                .map(entry -> toGroup(entry.getKey(), entry.getValue(), spanNames, traceStartMs))
                .sorted(Comparator.comparingInt((TraceNotificationGroup group) -> rankOf(group.severity()))
                        .thenComparing(Comparator.comparingLong(TraceNotificationGroup::count).reversed())
                        .thenComparing(TraceNotificationGroup::type, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new TraceNotificationSummary(
                groups,
                notifications.size(),
                notifications.stream().filter(row -> isUrgent(row.severity())).count());
    }

    static boolean isUrgent(String severity) {
        return severity != null && URGENT_SEVERITIES.contains(severity);
    }

    private static int rankOf(String severity) {
        if (severity == null) {
            return UNRANKED_SEVERITY;
        }
        return SEVERITY_RANK.getOrDefault(severity, UNRANKED_SEVERITY);
    }

    private static TraceNotificationGroup toGroup(
            Key key, List<TraceNotificationRow> rows, Map<String, String> spanNames, long traceStartMs) {

        List<TraceNotificationGroup.Site> sites = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> siteOf(row, spanNames),
                        LinkedHashMap::new,
                        Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new TraceNotificationGroup.Site(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(TraceNotificationGroup.Site::count).reversed()
                        .thenComparing(TraceNotificationGroup.Site::spanName))
                .toList();

        TraceNotificationRow first = rows.getFirst();
        return new TraceNotificationGroup(
                key.type(),
                key.severity(),
                first.category(),
                first.source(),
                key.message(),
                rows.size(),
                rows.stream().mapToLong(TraceNotificationRow::startMillisFromBeginning).min().orElse(traceStartMs) - traceStartMs,
                rows.stream().mapToLong(TraceNotificationRow::startMillisFromBeginning).max().orElse(traceStartMs) - traceStartMs,
                first.attributes(),
                sites);
    }

    private static String siteOf(TraceNotificationRow row, Map<String, String> spanNames) {
        if (row.spanId() == null) {
            return NO_SPAN;
        }
        return spanNames.getOrDefault(row.spanId(), UNKNOWN_SPAN);
    }

    boolean isEmpty() {
        return groups.isEmpty();
    }
}
