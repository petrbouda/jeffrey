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

import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.test.volume.VolumeWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Four chunks of one real recording session, as async-profiler left them in a session directory of
 * a Jeffrey volume: a Jeffrey on JDK 25.0.1 profiled with {@code loop=15m,event=ctimer,alloc,lock},
 * one chunk every fifteen minutes, each a whole JFR file of one JFR chunk. The session had started
 * 45 minutes before the first of them, so its first three chunks are no longer here — the way
 * retention leaves a long session.
 *
 * <p>The figures below are what {@code jfr summary} reads from each file, so a test can pin what a
 * window over the session must hold rather than check only that something came back.
 */
public final class RecordedSession {

    /** The session's name on the volume it came from: a UUIDv7 carrying its start. */
    public static final String SESSION_ID = "019b38f0-6d9d-7589-9648-310bfd95f362";

    /** The start {@link #SESSION_ID} carries in its top 48 bits. */
    public static final Instant SESSION_START = Instant.parse("2025-12-19T23:27:19.453Z");

    /** The profiler's loop, which the spacing of the chunks' names measures. */
    public static final Duration CHUNK_LENGTH = Duration.ofMinutes(15);

    /** The machine wrote its chunk names in UTC. */
    public static final ZoneOffset OFFSET = ZoneOffset.UTC;

    public static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
    public static final String ALLOCATION_IN_NEW_TLAB = "jdk.ObjectAllocationInNewTLAB";
    public static final String THREAD_PARK = "jdk.ThreadPark";
    public static final String MONITOR_ENTER = "jdk.JavaMonitorEnter";

    private static final String ARCHIVE_SUFFIX = ".lz4";

    public static final List<Chunk> CHUNKS = List.of(
            new Chunk("chunk-1.jfr", Instant.parse("2025-12-20T00:12:24Z"), 261, 116, 11, 0),
            new Chunk("chunk-2.jfr", Instant.parse("2025-12-20T00:27:24Z"), 237, 102, 5, 1),
            new Chunk("chunk-3.jfr", Instant.parse("2025-12-20T00:42:24Z"), 199, 114, 8, 0),
            new Chunk("chunk-4.jfr", Instant.parse("2025-12-20T00:57:24Z"), 191, 124, 7, 0));

    /** Where the recorded data ends: the newest chunk's start plus one chunk length. */
    public static final Instant DATA_END = CHUNKS.getLast().openedAt().plus(CHUNK_LENGTH);

    private RecordedSession() {
    }

    /**
     * One chunk: the file it is kept in here, when the profiler opened it (the timestamp its name
     * carries on the volume, to the second), and how many events of four types it holds.
     *
     * @param executionSamples     its {@code jdk.ExecutionSample} events: CPU samples
     * @param allocationSamples    its {@code jdk.ObjectAllocationInNewTLAB} events
     * @param threadParks          its {@code jdk.ThreadPark} events
     * @param monitorEnters        its {@code jdk.JavaMonitorEnter} events: one contended lock in the session
     */
    public record Chunk(String resource, Instant openedAt,
                        int executionSamples, int allocationSamples, int threadParks, int monitorEnters) {

        /** The name the profiler gave it on the volume: {@code rec-20251220-001224+0000.jfr}. */
        public String volumeName() {
            return VolumeWriter.recordingName(openedAt, OFFSET);
        }

        /** The name the hub's compression job gives it: {@code rec-20251220-001224+0000.jfr.lz4}. */
        public String archivedName() {
            return volumeName() + ARCHIVE_SUFFIX;
        }

        /** Its id on the volume: the name without the extension, as a listing reports it. */
        public String fileId() {
            String name = volumeName();
            return name.substring(0, name.lastIndexOf('.'));
        }

        public byte[] bytes() {
            try (InputStream in = RecordedSession.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("A recorded chunk is missing from the classpath: " + resource);
                }
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** The CPU samples a run of chunks holds together. */
    public static int executionSamples(List<Chunk> chunks) {
        return chunks.stream().mapToInt(Chunk::executionSamples).sum();
    }

    /** The allocation samples a run of chunks holds together. */
    public static int allocationSamples(List<Chunk> chunks) {
        return chunks.stream().mapToInt(Chunk::allocationSamples).sum();
    }

    /** The contended monitor enters a run of chunks holds together. */
    public static int monitorEnters(List<Chunk> chunks) {
        return chunks.stream().mapToInt(Chunk::monitorEnters).sum();
    }

    /** The thread parks a run of chunks holds together. */
    public static int threadParks(List<Chunk> chunks) {
        return chunks.stream().mapToInt(Chunk::threadParks).sum();
    }

    /**
     * Lays the session down in an instance's directory, under its own name, with every chunk named
     * as the profiler named it.
     *
     * @return the session's directory
     */
    public static Path writeTo(Path instanceDir) {
        Path sessionDir = instanceDir.resolve(SESSION_ID);
        try {
            Files.createDirectories(sessionDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Chunk chunk : CHUNKS) {
            VolumeWriter.recording(sessionDir, chunk.openedAt(), OFFSET, chunk.bytes());
        }
        return sessionDir;
    }

    /**
     * Lays the session down as the hub's compression job leaves it: every chunk compressed to
     * {@code <name>.jfr.lz4} with the hub's compressor and the original removed, but the newest,
     * which the newest-file guard keeps as the profiler wrote it.
     *
     * @return the session's directory
     */
    public static Path writeAsTheHubLeavesIt(Path instanceDir) {
        Path sessionDir = writeTo(instanceDir);
        for (Chunk chunk : CHUNKS.subList(0, CHUNKS.size() - 1)) {
            Path plain = sessionDir.resolve(chunk.volumeName());
            Lz4Compressor.compress(plain, sessionDir.resolve(chunk.archivedName()));
            try {
                Files.delete(plain);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return sessionDir;
    }
}
