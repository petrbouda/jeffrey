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

import java.util.List;

/**
 * A run of sibling leaf spans sent as one summed row instead of one row each.
 * <p>
 * A run is every leaf under one parent sharing a name, an event type and an I/O origin, once there
 * are enough of them that drawing each is useless — a request that streams a body in small writes
 * makes hundreds of thousands. The members stay in the database; the reader asks for them a page
 * at a time, slowest first, when it unfolds the row.
 *
 * @param runId                 identifies the run within its trace: the hex id of its lowest member
 *                              span, which is stable across reads and safe in a URL path
 * @param parentSpanId          the span the members hang under, or {@code null} for a run of roots
 * @param position              where the run is drawn: before the span at this index of
 *                              {@link TraceDetail#spans()}, or after the last one when it equals
 *                              their count. The server places it — among its siblings, at its first
 *                              member's start — so the reader only splices
 * @param depth                 the members' depth in the tree, the same as any sibling's
 * @param name                  the members' shared name
 * @param kind                  the members' kind
 * @param eventType             the members' shared event type
 * @param ioOrigin              the members' shared I/O origin, {@code CLASS_LOADING} or {@code null}
 * @param synthesized           whether the members were synthesized from JDK events
 * @param threadHash            the thread the earliest member ran on
 * @param threadName            that thread's name, when the recording knew it
 * @param threadCount           how many distinct threads the members ran on
 * @param durations             the members' duration statistics
 * @param criticalPathNanos     the members' summed share of the trace's critical path
 * @param firstStartEpochMicros the earliest member start, UTC epoch micros
 * @param lastEndEpochMicros    the latest member end, UTC epoch micros
 * @param coverage              the trace window cut into equal slices, each the fraction (0..1) of
 *                              it that at least one member was running — what the row's lane draws
 *                              in place of one tick per member
 * @param entrySpanIds          the members a notification or an exception points at, so the reader
 *                              can resolve such an entry to this run without loading every member
 */
public record TraceSpanRunRow(
        String runId,
        String parentSpanId,
        int position,
        int depth,
        String name,
        String kind,
        String eventType,
        String ioOrigin,
        boolean synthesized,
        String threadHash,
        String threadName,
        int threadCount,
        TraceRunDurations durations,
        long criticalPathNanos,
        long firstStartEpochMicros,
        long lastEndEpochMicros,
        List<Double> coverage,
        List<String> entrySpanIds) {

    public TraceSpanRunRow {
        coverage = List.copyOf(coverage);
        entrySpanIds = List.copyOf(entrySpanIds);
    }
}
