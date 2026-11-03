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

/**
 * Two recordings of one service under load, kept in
 * <a href="https://github.com/petrbouda/jeffrey-recordings">jeffrey-recordings</a> (commit
 * {@code 3017ffe}) and copied byte for byte beside this class, LZ4-compressed as the hub's
 * compression job leaves a recording.
 *
 * <p>The same build of the {@code persons} service, profiled by async-profiler on JDK 21 with
 * {@code event=cpu,interval=5ms,wall=10ms,alloc,lock,jfrsync=default}, run twice: serialising
 * directly ({@code --efficient.mode=true}) and through a DOM ({@code false}). Each file holds five
 * JFR chunks in two chains that overlap in time: the JDK's own recording (chunks 0 and 1: GC,
 * socket reads, ...) and async-profiler's (chunks 2 to 4: its samples). The chains number their
 * event types differently, which is what makes such a file hard to read: {@code jfr summary} over
 * the whole file mislabels the profiler's types. Every figure below is what it reads from each chunk
 * on its own, summed.
 */
public enum PersonsRecording {

    DIRECT("jeffrey-persons-direct-serde-cpu.jfr.lz4",
            Instant.parse("2024-11-08T10:27:26.569Z"), Instant.parse("2024-11-08T10:42:08.450Z"),
            List.of(Instant.parse("2024-11-08T10:27:26Z"), Instant.parse("2024-11-08T10:40:12Z"),
                    Instant.parse("2024-11-08T10:27:26Z"), Instant.parse("2024-11-08T10:32:09Z"),
                    Instant.parse("2024-11-08T10:37:22Z")),
            new Counts(39_668, 863_971, 4_409_322, 62_342, 5_409, 1_562),
            List.of(
                    new ProfilerChunk(2, new Counts(21_339, 315_310, 1_401_711, 24_320, 1_839, 626)),
                    new ProfilerChunk(3, new Counts(10_679, 314_947, 1_558_809, 22_600, 2_223, 460)),
                    new ProfilerChunk(4, new Counts(7_650, 233_714, 1_448_802, 15_422, 1_347, 476)))),
    DOM("jeffrey-persons-dom-serde-cpu.jfr.lz4",
            Instant.parse("2024-11-08T10:44:02.913Z"), Instant.parse("2024-11-08T11:00:46.640Z"),
            List.of(Instant.parse("2024-11-08T10:44:02Z"), Instant.parse("2024-11-08T10:59:07Z"),
                    Instant.parse("2024-11-08T10:44:02Z"), Instant.parse("2024-11-08T10:48:34Z"),
                    Instant.parse("2024-11-08T10:53:59Z")),
            new Counts(37_282, 954_244, 5_035_236, 56_237, 4_083, 1_569),
            List.of(
                    new ProfilerChunk(2, new Counts(20_346, 314_794, 1_347_366, 23_668, 1_951, 808)),
                    new ProfilerChunk(3, new Counts(9_034, 326_080, 1_618_677, 18_388, 1_210, 349)),
                    new ProfilerChunk(4, new Counts(7_902, 313_370, 2_069_193, 14_181, 922, 412))));

    public static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
    public static final String WALL_CLOCK_SAMPLE = "profiler.WallClockSample";
    public static final String ALLOCATION_IN_NEW_TLAB = "jdk.ObjectAllocationInNewTLAB";
    public static final String THREAD_PARK = "jdk.ThreadPark";
    public static final String MONITOR_ENTER = "jdk.JavaMonitorEnter";

    private static final String RESOURCE_DIRECTORY = "persons/";

    private final String resource;
    private final Instant recordingStartedAt;
    private final Instant recordingFinishedAt;
    private final List<Instant> chunkStarts;
    private final Counts counts;
    private final List<ProfilerChunk> profilerChunks;

    PersonsRecording(String resource, Instant recordingStartedAt, Instant recordingFinishedAt,
                     List<Instant> chunkStarts, Counts counts, List<ProfilerChunk> profilerChunks) {
        this.resource = resource;
        this.recordingStartedAt = recordingStartedAt;
        this.recordingFinishedAt = recordingFinishedAt;
        this.chunkStarts = chunkStarts;
        this.counts = counts;
        this.profilerChunks = profilerChunks;
    }

    /** The file's name, as kept in jeffrey-recordings. */
    public String fileName() {
        return resource;
    }

    /** When the recording began, to the second: the name it is given on a volume. */
    public Instant startedAt() {
        return chunkStarts.getFirst();
    }

    /** The earliest start of any of its chunks, to the millisecond. */
    public Instant recordingStartedAt() {
        return recordingStartedAt;
    }

    /**
     * The latest end of any of its chunks, to the millisecond: the recording's span is from the
     * earliest start to here — not the chunks' durations added up, since its two chains overlap.
     */
    public Instant recordingFinishedAt() {
        return recordingFinishedAt;
    }

    /** When each of the file's chunks began, to the second, in the order the file holds them. */
    public List<Instant> chunkStarts() {
        return chunkStarts;
    }

    /** What the whole recording holds. */
    public Counts counts() {
        return counts;
    }

    /**
     * async-profiler's chain of chunks in the file, oldest first: what a profiler writing a file per
     * chunk would leave in its session. Together they hold every count of {@link #counts()}; the
     * JDK's chunks hold none of them.
     */
    public List<ProfilerChunk> profilerChunks() {
        return profilerChunks;
    }

    /** The compressed file, as kept in jeffrey-recordings. */
    public byte[] bytes() {
        try (InputStream in = PersonsRecording.class.getResourceAsStream(RESOURCE_DIRECTORY + resource)) {
            if (in == null) {
                throw new IllegalStateException("A recording is missing from the classpath: " + resource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * What a recording, or a part of it, holds of the profiler's event types.
     *
     * @param executionSamples  {@code jdk.ExecutionSample}: CPU samples, every 5 ms of CPU
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
     * One of async-profiler's chunks in the file.
     *
     * @param index  its position among the file's chunks
     * @param counts what it holds
     */
    public record ProfilerChunk(int index, Counts counts) {
    }

    /** When a profiler chunk began, to the second, as its header states: the name it is given on a volume. */
    public Instant openedAt(ProfilerChunk chunk) {
        return chunkStarts.get(chunk.index());
    }
}
