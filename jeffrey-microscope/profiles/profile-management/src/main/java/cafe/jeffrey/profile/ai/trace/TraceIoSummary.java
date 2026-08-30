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

import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.JsonNode;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The trace's socket and file I/O, grouped by what it was against.
 * <p>
 * Built from the spans the trace already carries rather than from a second query: the derivation
 * promotes every recorded {@code jdk.SocketRead}, {@code jdk.FileWrite} and friend into a leaf span
 * whose payload still holds the event's own fields, so the bytes and the path are already here. The
 * tree shows each operation where it happened; this shows what they add up to, which is the reading
 * the tree cannot give — four hundred bullets named "File read" say nothing about the buffer.
 * <p>
 * Computed over <em>every</em> span, including the ones the tree had to truncate. A trace whose
 * bullet list stops at four hundred spans still gets a complete I/O accounting, which is exactly the
 * trace whose I/O is worth accounting for.
 */
record TraceIoSummary(List<TraceIoTarget> targets, long operations, long bytes, long totalNanos) {

    /** One recorded operation, before its group swallows it. */
    private record Operation(TraceIoDirection direction, String target, long bytes, long nanos) {
    }

    /** What makes two operations the same row: the same kind of I/O against the same thing. */
    private record Key(TraceIoDirection direction, String target) {
    }

    static final TraceIoSummary EMPTY = new TraceIoSummary(List.of(), 0, 0, 0);

    static TraceIoSummary of(List<TraceSpanRow> spans) {
        List<Operation> operations = spans.stream()
                .map(TraceIoSummary::toOperation)
                .filter(Objects::nonNull)
                .toList();

        if (operations.isEmpty()) {
            return EMPTY;
        }

        Map<Key, List<Operation>> grouped = operations.stream()
                .collect(Collectors.groupingBy(op -> new Key(op.direction(), op.target())));

        // Ranked by cost, because that is the order the question is asked in: a target the trace
        // barely touched is not the one to explain, however odd its shape.
        List<TraceIoTarget> targets = grouped.entrySet().stream()
                .map(entry -> toTarget(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(TraceIoTarget::totalNanos).reversed()
                        .thenComparing(Comparator.comparingLong(TraceIoTarget::operations).reversed()))
                .toList();

        return new TraceIoSummary(
                targets,
                operations.size(),
                operations.stream().mapToLong(Operation::bytes).sum(),
                operations.stream().mapToLong(Operation::nanos).sum());
    }

    /**
     * One span read as an I/O operation, or {@code null} when it is not one.
     * <p>
     * A span with no payload still becomes an operation: the recording's threshold decided it was
     * worth an event, so it counts toward the shape even when its path went unrecorded. The field
     * readers take a null node, so the unknown target falls out rather than being branched on.
     */
    private static Operation toOperation(TraceSpanRow span) {
        return TraceIoDirection.of(span.eventType())
                .map(direction -> {
                    JsonNode fields = parseFields(span.eventFields());
                    return new Operation(
                            direction,
                            direction.target(fields),
                            direction.bytes(fields),
                            span.durationNanos());
                })
                .orElse(null);
    }

    private static JsonNode parseFields(String eventFields) {
        if (eventFields == null || eventFields.isBlank()) {
            return null;
        }
        return Json.readTree(eventFields);
    }

    private static TraceIoTarget toTarget(Key key, List<Operation> operations) {
        return new TraceIoTarget(
                key.direction(),
                key.target(),
                operations.size(),
                operations.stream().mapToLong(Operation::bytes).sum(),
                operations.stream().mapToLong(Operation::nanos).sum(),
                operations.stream().mapToLong(Operation::nanos).max().orElse(0L));
    }

    boolean isEmpty() {
        return targets.isEmpty();
    }
}
