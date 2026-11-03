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

package cafe.jeffrey.microscope.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

/**
 * Two recordings of one service under load, kept in
 * <a href="https://github.com/petrbouda/jeffrey-recordings">jeffrey-recordings</a> (commit
 * {@code 3017ffe}) and copied byte for byte into this module's test resources, LZ4-compressed as the
 * hub's compression job leaves a recording.
 *
 * <p>The same build of the {@code persons} service, profiled by async-profiler on JDK 21 with
 * {@code event=cpu,interval=5ms,wall=10ms,alloc,lock,jfrsync=default}, run twice: serialising
 * directly ({@code --efficient.mode=true}) and through a DOM ({@code false}). Each file holds five
 * JFR chunks: two of the JDK's own recording (GC, socket reads, ...) and three of async-profiler's,
 * overlapping in time. The figures below are what {@code jfr summary} reads from each chunk on its
 * own — read over the whole file it mislabels the profiler's event types, whose ids differ from the
 * JDK's chunk to chunk.
 *
 * @param resource          the file under {@code recordings/persons/} on the test classpath
 * @param startedAt         when the recording began, to the second: the name it is given on the volume
 * @param executionSamples  {@code jdk.ExecutionSample}: CPU samples, every 5 ms of CPU
 * @param wallClockSamples  {@code profiler.WallClockSample}, every thread every 10 ms: the samples, not the
 *                          events — async-profiler folds an idle thread's samples into one event and
 *                          counts them in its {@code samples} field (863,971 events direct, 954,244 DOM)
 * @param allocationSamples {@code jdk.ObjectAllocationInNewTLAB}
 * @param threadParks       {@code jdk.ThreadPark}
 * @param monitorEnters     {@code jdk.JavaMonitorEnter}: contended locks
 */
enum PersonsRecording {

    DIRECT("jeffrey-persons-direct-serde-cpu.jfr.lz4", Instant.parse("2024-11-08T10:27:26Z"),
            39_668, 4_409_322, 62_342, 5_409, 1_562, List.of(
            new ProfilerChunk(2, Instant.parse("2024-11-08T10:27:26Z"), 21_339, 1_401_711, 24_320, 1_839, 626),
            new ProfilerChunk(3, Instant.parse("2024-11-08T10:32:09Z"), 10_679, 1_558_809, 22_600, 2_223, 460),
            new ProfilerChunk(4, Instant.parse("2024-11-08T10:37:22Z"), 7_650, 1_448_802, 15_422, 1_347, 476))),
    DOM("jeffrey-persons-dom-serde-cpu.jfr.lz4", Instant.parse("2024-11-08T10:44:02Z"),
            37_282, 5_035_236, 56_237, 4_083, 1_569, List.of(
            new ProfilerChunk(2, Instant.parse("2024-11-08T10:44:02Z"), 20_346, 1_347_366, 23_668, 1_951, 808),
            new ProfilerChunk(3, Instant.parse("2024-11-08T10:48:34Z"), 9_034, 1_618_677, 18_388, 1_210, 349),
            new ProfilerChunk(4, Instant.parse("2024-11-08T10:53:59Z"), 7_902, 2_069_193, 14_181, 922, 412)));

    static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
    static final String WALL_CLOCK_SAMPLE = "profiler.WallClockSample";
    static final String ALLOCATION_IN_NEW_TLAB = "jdk.ObjectAllocationInNewTLAB";
    static final String THREAD_PARK = "jdk.ThreadPark";
    static final String MONITOR_ENTER = "jdk.JavaMonitorEnter";

    private static final String RESOURCE_DIRECTORY = "/recordings/persons/";

    private final String resource;
    private final Instant startedAt;
    private final int executionSamples;
    private final int wallClockSamples;
    private final int allocationSamples;
    private final int threadParks;
    private final int monitorEnters;
    private final List<ProfilerChunk> profilerChunks;

    PersonsRecording(String resource, Instant startedAt, int executionSamples, int wallClockSamples,
                     int allocationSamples, int threadParks, int monitorEnters, List<ProfilerChunk> profilerChunks) {
        this.resource = resource;
        this.startedAt = startedAt;
        this.executionSamples = executionSamples;
        this.wallClockSamples = wallClockSamples;
        this.allocationSamples = allocationSamples;
        this.threadParks = threadParks;
        this.monitorEnters = monitorEnters;
        this.profilerChunks = profilerChunks;
    }

    Instant startedAt() {
        return startedAt;
    }

    int executionSamples() {
        return executionSamples;
    }

    int wallClockSamples() {
        return wallClockSamples;
    }

    int allocationSamples() {
        return allocationSamples;
    }

    int threadParks() {
        return threadParks;
    }

    int monitorEnters() {
        return monitorEnters;
    }

    /**
     * async-profiler's chain of chunks in the file — the JDK's two chunks (0 and 1) overlap it in
     * time and hold none of the profiler's events — oldest first: what a profiler writing a file per
     * chunk would leave in its session. Together they hold every count above.
     */
    List<ProfilerChunk> profilerChunks() {
        return profilerChunks;
    }

    /** The compressed file, as kept in jeffrey-recordings. */
    byte[] bytes() {
        try (InputStream in = PersonsRecording.class.getResourceAsStream(RESOURCE_DIRECTORY + resource)) {
            if (in == null) {
                throw new IllegalStateException("A recording is missing from the test classpath: " + resource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * One of async-profiler's chunks in the file, as {@code jfr summary} reads it on its own.
     *
     * @param index    its position among the file's chunks
     * @param openedAt when it began, to the second, as its header states: the name it is given on the volume
     */
    record ProfilerChunk(int index, Instant openedAt, int executionSamples, int wallClockSamples,
                         int allocationSamples, int threadParks, int monitorEnters) {
    }
}
