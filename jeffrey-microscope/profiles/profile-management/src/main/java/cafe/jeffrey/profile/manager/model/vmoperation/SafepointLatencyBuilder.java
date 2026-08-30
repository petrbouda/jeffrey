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

package cafe.jeffrey.profile.manager.model.vmoperation;

import org.HdrHistogram.Histogram;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups {@code jdk.SafepointLatency} by the thread that was waited for, ranked by summed latency.
 * <p>
 * Aggregation is not a presentation choice here, it is forced by the event: JFR writes one of these
 * per thread per safepoint, so a recording that saw 16 safepoints across a small application
 * already produced 584 of them, and a real one produces orders of magnitude more. Listing them
 * would bury the finding in its own evidence.
 * <p>
 * Ranked by total rather than by max, because the page is looking for a habit. A thread that is
 * slow to yield on every one of a thousand safepoints is a tuning problem worth finding; one that
 * was slow once is usually a scheduler hiccup, and sorting by max would put the hiccup on top. Both
 * numbers are kept so the reader can tell those apart.
 */
public class SafepointLatencyBuilder implements RecordBuilder<GenericRecord, SafepointLatencyData> {

    /** A page ranks rather than enumerates; the thread count travels so the cap is not silent. */
    private static final int MAX_OFFENDERS = 20;

    private static final String THREAD_STATE_FIELD = "threadState";
    private static final String UNKNOWN_THREAD = "<unknown>";
    private static final String UNKNOWN_STATE = "<unknown>";

    /** Three significant digits, matching the other percentile builders in this codebase. */
    private static final int HISTOGRAM_PRECISION = 3;

    private static final double P99 = 99.0;

    private static final class Accumulator {
        private final Histogram histogram = new Histogram(HISTOGRAM_PRECISION);
        private String threadState = UNKNOWN_STATE;
        private long count;
        private long totalNanos;
        private long maxNanos;
    }

    private final Map<String, Accumulator> byThread = new HashMap<>();
    private long worstNanos;
    private long totalNanos;

    @Override
    public void onRecord(GenericRecord record) {
        if (record.duration() == null) {
            return;
        }
        long nanos = record.duration().toNanos();
        if (nanos < 0) {
            return;
        }

        String threadName = record.thread() != null && record.thread().name() != null
                ? record.thread().name()
                : UNKNOWN_THREAD;

        Accumulator accumulator = byThread.computeIfAbsent(threadName, _ -> new Accumulator());
        accumulator.count++;
        accumulator.totalNanos += nanos;
        accumulator.maxNanos = Math.max(accumulator.maxNanos, nanos);
        accumulator.histogram.recordValue(nanos);

        /*
         * The state of the worst sample rather than the most recent one: a thread that is usually
         * running Java and once blocked in native is described by the sample that actually held the
         * JVM up, which is the one the reader is being asked to act on.
         */
        if (nanos == accumulator.maxNanos) {
            accumulator.threadState = threadState(record);
        }

        worstNanos = Math.max(worstNanos, nanos);
        totalNanos += nanos;
    }

    private static String threadState(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        if (fields == null || !fields.has(THREAD_STATE_FIELD)) {
            return UNKNOWN_STATE;
        }
        String state = fields.get(THREAD_STATE_FIELD).asString();
        return state == null || state.isBlank() ? UNKNOWN_STATE : state;
    }

    @Override
    public SafepointLatencyData build() {
        if (byThread.isEmpty()) {
            return SafepointLatencyData.EMPTY;
        }

        List<SafepointOffender> offenders = new ArrayList<>(byThread.size());
        for (Map.Entry<String, Accumulator> entry : byThread.entrySet()) {
            Accumulator accumulator = entry.getValue();
            offenders.add(new SafepointOffender(
                    entry.getKey(),
                    accumulator.threadState,
                    accumulator.count,
                    accumulator.maxNanos,
                    accumulator.histogram.getValueAtPercentile(P99),
                    accumulator.totalNanos));
        }
        offenders.sort(Comparator.comparingLong(SafepointOffender::totalNanos).reversed());

        return new SafepointLatencyData(
                List.copyOf(offenders.subList(0, Math.min(MAX_OFFENDERS, offenders.size()))),
                byThread.size(),
                worstNanos,
                totalNanos);
    }
}
