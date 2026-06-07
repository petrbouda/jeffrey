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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.provider.profile.api.SpanEventRecord;
import cafe.jeffrey.provider.profile.api.SpanRecord;
import cafe.jeffrey.provider.profile.api.SpanRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates a deterministic set of {@code profiler.Span} events themed around "Jeffrey profiling
 * itself" — no database access. Selected at runtime via {@code jeffrey.profile.spans.mock=true} so
 * the span views are demoable before async-profiler's Span API ships. Swap for
 * {@code JdbcSpanRepository} by setting the flag to {@code false}.
 */
public class MockSpanRepository implements SpanRepository {

    private static final long NANOS_PER_MILLI = 1_000_000L;
    // Fixed base so the generated timeline is deterministic (2026-06-05T10:00:00Z).
    private static final long BASE_EPOCH_MILLIS = 1_780_999_200_000L;
    private static final long SAMPLE_INTERVAL_MILLIS = 110L;

    private static final long OS_HTTP_3 = 41L;
    private static final long OS_FJP_WORKER_1 = 58L;
    private static final long OS_HPROF = 71L;
    private static final long OS_HTTP_7 = 44L;

    private final List<SpanRecord> spans = build();

    @Override
    public List<SpanRecord> listSpans() {
        return spans;
    }

    @Override
    public List<SpanEventRecord> eventsForThread(long osThreadId, long fromEpochMillis, long toEpochMillis) {
        if (toEpochMillis <= fromEpochMillis) {
            return List.of();
        }
        long window = toEpochMillis - fromEpochMillis;
        List<SpanEventRecord> result = new ArrayList<>();

        // CPU samples spread evenly across the window.
        for (long t = fromEpochMillis; t <= toEpochMillis; t += SAMPLE_INTERVAL_MILLIS) {
            result.add(new SpanEventRecord("jdk.ExecutionSample", t, 0, null));
        }
        // Periodic allocation samples.
        for (long t = fromEpochMillis + 250; t <= toEpochMillis; t += 700) {
            result.add(new SpanEventRecord("jdk.ObjectAllocationSample", t, 0, "{\"objectClass\":\"byte[]\"}"));
        }
        // A couple of monitor-enter events with duration.
        if (window > 400) {
            result.add(new SpanEventRecord(
                    "jdk.JavaMonitorEnter", fromEpochMillis + window / 3, 3_200_000L,
                    "{\"monitorClass\":\"java.lang.Object\"}"));
            result.add(new SpanEventRecord(
                    "jdk.JavaMonitorEnter", fromEpochMillis + 2 * window / 3, 1_800_000L,
                    "{\"monitorClass\":\"java.util.concurrent.locks.ReentrantLock\"}"));
        }
        // One park near the end.
        if (window > 200) {
            result.add(new SpanEventRecord("jdk.ThreadPark", toEpochMillis - 100, 90_000_000L, null));
        }

        result.sort(Comparator.comparingLong(SpanEventRecord::startEpochMillis));
        return List.copyOf(result);
    }

    private static List<SpanRecord> build() {
        List<SpanRecord> result = new ArrayList<>();

        // Thread http-nio-exec-3: a request whose work nests several levels deep.
        result.add(span("GET /api/recordings", 0, 6_300, OS_HTTP_3, 12));
        result.add(span("profile.initialize", 300, 5_950, OS_HTTP_3, 12));
        result.add(span("jfr.parse_and_ingest", 320, 5_080, OS_HTTP_3, 12));
        result.add(span("duckdb.batch.flush", 1_000, 200, OS_HTTP_3, 12));
        result.add(span("duckdb.batch.flush", 2_500, 180, OS_HTTP_3, 12));
        result.add(span("duckdb.batch.flush", 4_200, 220, OS_HTTP_3, 12));

        // Thread ForkJoinPool-1-worker-1: parallel chunk parsing.
        result.add(span("jfr.parse_and_ingest", 340, 2_600, OS_FJP_WORKER_1, 21));
        result.add(span("jfr.parse_and_ingest", 3_000, 2_200, OS_FJP_WORKER_1, 21));

        // Thread hprof-indexer: heap dump indexing with a nested pass.
        result.add(span("hprof.index.build", 8_050, 3_400, OS_HPROF, 33));
        result.add(span("hprof.passB", 9_000, 2_000, OS_HPROF, 33));

        // Thread http-nio-exec-7: repeated flamegraph renders plus one slow AI call.
        long flamegraphStart = 8_200;
        long[] flamegraphDurations = {320, 540, 410, 690, 300, 470};
        for (long duration : flamegraphDurations) {
            result.add(span("flamegraph.generate", flamegraphStart, duration, OS_HTTP_7, 14));
            flamegraphStart += 600;
        }
        result.add(span("ai.oql.call", 9_700, 2_350, OS_HTTP_7, 14));

        return List.copyOf(result);
    }

    private static SpanRecord span(String tag, long startMillis, long durationMillis, long osThreadId, long javaThreadId) {
        return new SpanRecord(
                startMillis,
                BASE_EPOCH_MILLIS + startMillis,
                durationMillis * NANOS_PER_MILLI,
                osThreadId,
                javaThreadId,
                threadName(osThreadId),
                tag);
    }

    private static String threadName(long osThreadId) {
        if (osThreadId == OS_HTTP_3) {
            return "http-nio-exec-3";
        }
        if (osThreadId == OS_FJP_WORKER_1) {
            return "ForkJoinPool-1-worker-1";
        }
        if (osThreadId == OS_HPROF) {
            return "hprof-indexer";
        }
        if (osThreadId == OS_HTTP_7) {
            return "http-nio-exec-7";
        }
        return "thread-" + osThreadId;
    }
}
