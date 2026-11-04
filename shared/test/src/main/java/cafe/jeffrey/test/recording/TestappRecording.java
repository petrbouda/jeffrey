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

package cafe.jeffrey.test.recording;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Two runs of {@code jeffrey-testapp-server}, a service that keeps persons in a database behind a REST
 * API and traces every request, each a session the provisioner laid down under the shared volume and
 * copied from it byte for byte beside this class: the session's recording and its JVM's
 * {@code perf-counters.hsperfdata}.
 *
 * <p>The same build, run twice under the same load, profiled by async-profiler with wall-clock, CPU,
 * allocation and lock sampling and {@code jfrsync}, with jeffrey-events and jeffrey-tracing on its class
 * path: writing each person straight to the response ({@code testapp.mode = direct}), or through a JSON
 * tree first ({@code dom}) — which only the DOM run traces, as {@code person.to-json-tree} spans. Each
 * file holds three JFR chunks in two chains that overlap in time: the JDK's own recording (chunks 0 and
 * 1, rotated once: every request's spans, its JDBC statements and the connection pool's events) and
 * async-profiler's (chunk 2: its samples). The chains number their event types differently, so every
 * figure below is what each chunk read on its own holds, summed.
 */
public enum TestappRecording {

    DIRECT("direct", "direct-jeffrey-testapp-server", "direct-jeffrey-testapp-server-57ff887d-64tt8",
            "01a1a2d4-3750-77d1-90e0-3a6882ec4952",
            List.of(Instant.parse("2026-11-03T19:13:00.691955389Z"), Instant.parse("2026-11-03T19:14:04.245320078Z"),
                    Instant.parse("2026-11-03T19:13:01.387971Z")),
            Instant.parse("2026-11-03T19:14:16.777601Z"),
            new Counts(4_628, 19_623, 57_602, 6_161, 26, 556),
            new Traces(12_349, 7_968, 11_148, 663, 527, 12_339, 67, 160_609_200L,
                    Map.of("GET /persons/count", 7_163, "GET /persons", 3_320, "GET /persons/{count}", 664,
                            "POST /persons", 663, "DELETE /persons/{id}", 527, "GET /actuator/health/**", 12),
                    Map.of("person.redact-opinions", 3_984, "person.pick-id", 3_320, "person.pick-ids", 664),
                    Map.of("count_query", 7_163, "find_by_id", 3_320, "find_all_by_ids", 664,
                            "insert_person", 663, "remove_query_by_id", 527, "all_ids_query", 1)),
            new PerfCounters(191, 8, 9_311, 38, 18)),
    DOM("dom", "dom-jeffrey-testapp-server", "dom-jeffrey-testapp-server-7bcf4bd5d-5hhmq",
            "01a1a2d4-3744-764d-b870-1f82cb6c8d67",
            List.of(Instant.parse("2026-11-03T19:13:00.689363801Z"), Instant.parse("2026-11-03T19:13:55.134184745Z"),
                    Instant.parse("2026-11-03T19:13:01.396482Z")),
            Instant.parse("2026-11-03T19:14:16.782083Z"),
            new Counts(5_097, 20_978, 57_373, 7_799, 42, 586),
            new Traces(12_348, 19_765, 15_122, 664, 531, 16_318, 67, 174_546_650L,
                    Map.of("GET /persons/count", 7_161, "GET /persons", 3_316, "GET /persons/{count}", 664,
                            "POST /persons", 664, "DELETE /persons/{id}", 531, "GET /actuator/health/**", 12),
                    Map.of("person.to-json-tree", 11_805, "person.redact-opinions", 3_980, "person.pick-id", 3_316,
                            "person.pick-ids", 664),
                    Map.of("count_query", 11_141, "find_by_id", 3_316, "find_all_by_ids", 664,
                            "insert_person", 664, "remove_query_by_id", 531, "all_ids_query", 1)),
            new PerfCounters(191, 9, 9_327, 42, 22));

    /** The workspace the provisioner laid both sessions down in. */
    public static final String WORKSPACE = "$default";

    /** The session's recording, named by the provisioner's template for when the session began. */
    public static final String RECORDING_FILE = "rec-20261103-191259+0000.jfr";
    /** The JVM's own counters, which the provisioner keeps beside the recording. */
    public static final String PERF_COUNTERS_FILE = "perf-counters.hsperfdata";

    public static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
    public static final String WALL_CLOCK_SAMPLE = "profiler.WallClockSample";
    public static final String ALLOCATION_IN_NEW_TLAB = "jdk.ObjectAllocationInNewTLAB";
    public static final String THREAD_PARK = "jdk.ThreadPark";
    public static final String MONITOR_ENTER = "jdk.JavaMonitorEnter";

    public static final String HTTP_SERVER_EXCHANGE = "jeffrey.HttpServerExchange";
    public static final String TRACE_SPAN = "jeffrey.TraceSpan";
    public static final String JDBC_QUERY = "jeffrey.JdbcQuery";
    public static final String JDBC_UPDATE = "jeffrey.JdbcUpdate";
    public static final String JDBC_DELETE = "jeffrey.JdbcDelete";
    public static final String CONNECTION_ACQUIRED = "jeffrey.PooledJdbcConnectionAcquired";
    public static final String POOL_STATISTICS = "jeffrey.JdbcPoolStatistics";

    /** The attribute every request's root carries, its value the run's mode; its name holds a dot. */
    public static final String MODE = "testapp.mode";
    /**
     * The one statement run outside any request: the ids read once at startup. No span was open, so the
     * tracer left its ids at 0 — it belongs to no trace.
     */
    public static final String STARTUP_QUERY = "all_ids_query";

    /** The JVM both runs ran on, as its perf counters name it. */
    public static final String VM_VERSION = "25.0.5+8-LTS";
    /** The span only the DOM run records: a person turned into a JSON tree before it is written. */
    public static final String TO_JSON_TREE = "person.to-json-tree";

    /** The index, among the file's chunks, of async-profiler's one chunk: every sample is in it. */
    public static final int PROFILER_CHUNK = 2;

    private static final String RESOURCE_DIRECTORY = "testapp/";

    private final String mode;
    private final String project;
    private final String instance;
    private final String sessionId;
    private final List<Instant> chunkStarts;
    private final Instant recordingFinishedAt;
    private final Counts counts;
    private final Traces traces;
    private final PerfCounters perfCounterFigures;

    TestappRecording(String mode, String project, String instance, String sessionId, List<Instant> chunkStarts,
                     Instant recordingFinishedAt, Counts counts, Traces traces, PerfCounters perfCounterFigures) {
        this.mode = mode;
        this.project = project;
        this.instance = instance;
        this.sessionId = sessionId;
        this.chunkStarts = chunkStarts;
        this.recordingFinishedAt = recordingFinishedAt;
        this.counts = counts;
        this.traces = traces;
        this.perfCounterFigures = perfCounterFigures;
    }

    /** The run's {@code testapp.mode}: {@code direct} or {@code dom}. */
    public String mode() {
        return mode;
    }

    /** The project the provisioner laid the session down in. */
    public String project() {
        return project;
    }

    /** The pod the service ran in. */
    public String instance() {
        return instance;
    }

    /** The session's directory, as the provisioner named it. */
    public String sessionId() {
        return sessionId;
    }

    /** When each of the file's chunks began, to the nanosecond its header states, in the order the file holds them. */
    public List<Instant> chunkStarts() {
        return chunkStarts;
    }

    /** The earliest start of any of its chunks: the JDK's first. */
    public Instant recordingStartedAt() {
        return chunkStarts.stream().min(Instant::compareTo).orElseThrow();
    }

    /**
     * The latest end of any of its chunks: the recording's span is from the earliest start to here —
     * not the chunks' durations added up, since its two chains overlap.
     */
    public Instant recordingFinishedAt() {
        return recordingFinishedAt;
    }

    /** What async-profiler's chunk holds — the whole recording's samples. */
    public Counts counts() {
        return counts;
    }

    /** What the JDK's chunks hold of the service's own events. */
    public Traces traces() {
        return traces;
    }

    /** What the JVM's counters held when the provisioner took them, as a reader of the format finds them. */
    public PerfCounters perfCounterFigures() {
        return perfCounterFigures;
    }

    /** The session's recording, as the provisioner left it. */
    public byte[] bytes() {
        return resource(RECORDING_FILE);
    }

    /** The JVM's counters, as the provisioner left them beside the recording. */
    public byte[] perfCounters() {
        return resource(PERF_COUNTERS_FILE);
    }

    private byte[] resource(String name) {
        String path = RESOURCE_DIRECTORY + mode + "/" + name;
        try (InputStream in = TestappRecording.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("A recording is missing from the classpath: " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * What a recording holds of the profiler's event types.
     *
     * @param executionSamples  {@code jdk.ExecutionSample}: CPU samples
     * @param wallClockEvents   {@code profiler.WallClockSample} events
     * @param wallClockSamples  the samples those events carry: async-profiler folds an idle thread's
     *                          samples into one event and counts them in its {@code samples} field
     * @param allocationSamples {@code jdk.ObjectAllocationInNewTLAB}
     * @param threadParks       {@code jdk.ThreadPark}
     * @param monitorEnters     {@code jdk.JavaMonitorEnter}: contended locks
     */
    public record Counts(int executionSamples, int wallClockEvents, int wallClockSamples,
                         int allocationSamples, int threadParks, int monitorEnters) {
    }

    /**
     * What the JDK's chain holds of the service's own events: every request a trace rooted in its
     * exchange, the steps and statements under it, and the connection pool's events.
     *
     * @param exchanges            {@code jeffrey.HttpServerExchange}: one per request, its trace's root
     * @param spans                {@code jeffrey.TraceSpan}: the requests' internal steps
     * @param queries              {@code jeffrey.JdbcQuery}
     * @param updates              {@code jeffrey.JdbcUpdate}
     * @param deletes              {@code jeffrey.JdbcDelete}
     * @param connectionsAcquired  {@code jeffrey.PooledJdbcConnectionAcquired}
     * @param poolStatistics       {@code jeffrey.JdbcPoolStatistics}
     * @param slowestNanos         the longest request, as its exchange recorded it
     * @param requestsByOperation  the exchanges by the operation each names
     * @param stepsByName          the internal spans by name
     * @param statementsByName     the JDBC statements by name; every one but {@link #STARTUP_QUERY} inside a request
     */
    public record Traces(int exchanges, int spans, int queries, int updates, int deletes, int connectionsAcquired,
                         int poolStatistics, long slowestNanos, Map<String, Integer> requestsByOperation,
                         Map<String, Integer> stepsByName, Map<String, Integer> statementsByName) {

        /** Every JDBC statement the run recorded. */
        public int statements() {
            return queries + updates + deletes;
        }

        /** Every JDBC statement run inside a request: all but {@link #STARTUP_QUERY}. */
        public int tracedStatements() {
            return statements() - statementsByName.get(STARTUP_QUERY);
        }
    }

    /**
     * What the JVM's perf counters held, read straight from the file's HotSpot format.
     *
     * @param entries          how many counters the file holds
     * @param liveThreads      {@code java.threads.live}
     * @param loadedClasses    {@code java.cls.loadedClasses}
     * @param safepoints       {@code sun.rt.safepoints}
     * @param youngCollections {@code sun.gc.collector.0.invocations}: as many as the recording's
     *                         {@code jdk.YoungGarbageCollection} events
     */
    public record PerfCounters(int entries, int liveThreads, int loadedClasses, int safepoints, int youngCollections) {
    }
}
