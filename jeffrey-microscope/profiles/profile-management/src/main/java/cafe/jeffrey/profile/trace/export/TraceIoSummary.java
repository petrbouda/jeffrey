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

import cafe.jeffrey.profile.manager.model.trace.TracePromotedGroup;
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
 * Built from the promoted spans totalled per event type and payload: the derivation promotes every
 * recorded {@code jdk.SocketRead}, {@code jdk.FileWrite} and friend into a leaf span whose payload
 * still holds the event's own fields, so the bytes and the path are there — and a payload is stored
 * once however many operations share it, so a million writes to one buffer arrive as a handful of
 * groups. The tree shows each operation where it happened; this shows what they add up to, which is
 * the reading the tree cannot give — four hundred bullets named "File read" say nothing about the
 * buffer.
 * <p>
 * Computed over <em>every</em> operation, including the ones the tree folded or truncated. A trace
 * whose bullet list stops at four hundred spans still gets a complete I/O accounting, which is
 * exactly the trace whose I/O is worth accounting for.
 */
record TraceIoSummary(List<TraceIoTarget> targets, long operations, long bytes, long totalNanos) {

    /** Operations that shared one payload, before their target's group swallows them. */
    private record Operation(
            TraceIoDirection direction, String target, long count, long bytes, long nanos, long maxNanos) {
    }

    /** What makes two operations the same row: the same kind of I/O against the same thing. */
    private record Key(TraceIoDirection direction, String target) {
    }

    static final TraceIoSummary EMPTY = new TraceIoSummary(List.of(), 0, 0, 0);

    static TraceIoSummary of(List<TracePromotedGroup> groups) {
        List<Operation> operations = groups.stream()
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
                operations.stream().mapToLong(Operation::count).sum(),
                operations.stream().mapToLong(Operation::bytes).sum(),
                operations.stream().mapToLong(Operation::nanos).sum());
    }

    /**
     * One group read as I/O operations, or {@code null} when its event type is not I/O.
     * <p>
     * A group with no payload still becomes operations: the recording's threshold decided each was
     * worth an event, so they count toward the shape even when their path went unrecorded. The field
     * readers take a null node, so the unknown target falls out rather than being branched on. Every
     * operation in a group carried the same payload, so the bytes one moved is what each moved.
     */
    private static Operation toOperation(TracePromotedGroup group) {
        return TraceIoDirection.of(group.eventType())
                .map(direction -> {
                    JsonNode fields = parseFields(group.eventFields());
                    return new Operation(
                            direction,
                            direction.target(fields),
                            group.count(),
                            direction.bytes(fields) * group.count(),
                            group.totalNanos(),
                            group.maxNanos());
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
                operations.stream().mapToLong(Operation::count).sum(),
                operations.stream().mapToLong(Operation::bytes).sum(),
                operations.stream().mapToLong(Operation::nanos).sum(),
                operations.stream().mapToLong(Operation::maxNanos).max().orElse(0L));
    }

    boolean isEmpty() {
        return targets.isEmpty();
    }
}
