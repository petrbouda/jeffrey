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

package cafe.jeffrey.profile.parser;

import cafe.jeffrey.jfrparser.raw.JfrParser;
import cafe.jeffrey.provider.profile.api.RecordingSources;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.test.recording.CheckoutRecording;
import cafe.jeffrey.test.recording.TestappRecording;
import cafe.jeffrey.test.recording.TestappRecording.Counts;
import cafe.jeffrey.test.recording.TestappRecording.Traces;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static cafe.jeffrey.test.recording.TestappRecording.ALLOCATION_IN_NEW_TLAB;
import static cafe.jeffrey.test.recording.TestappRecording.CONNECTION_ACQUIRED;
import static cafe.jeffrey.test.recording.TestappRecording.DIRECT;
import static cafe.jeffrey.test.recording.TestappRecording.EXECUTION_SAMPLE;
import static cafe.jeffrey.test.recording.TestappRecording.HTTP_SERVER_EXCHANGE;
import static cafe.jeffrey.test.recording.TestappRecording.JDBC_DELETE;
import static cafe.jeffrey.test.recording.TestappRecording.JDBC_QUERY;
import static cafe.jeffrey.test.recording.TestappRecording.JDBC_UPDATE;
import static cafe.jeffrey.test.recording.TestappRecording.MONITOR_ENTER;
import static cafe.jeffrey.test.recording.TestappRecording.POOL_STATISTICS;
import static cafe.jeffrey.test.recording.TestappRecording.PROFILER_CHUNK;
import static cafe.jeffrey.test.recording.TestappRecording.THREAD_PARK;
import static cafe.jeffrey.test.recording.TestappRecording.TRACE_SPAN;
import static cafe.jeffrey.test.recording.TestappRecording.WALL_CLOCK_SAMPLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The event parser over realistic recordings: files a profiler with {@code jfrsync} wrote, each two
 * chains of chunks overlapping in time — the JDK's own and async-profiler's — whose event types are
 * numbered differently. What reaches the writer is held to what {@link TestappRecording} pins, however
 * the file is read: as the provisioner left it, compressed as the hub leaves it, split, or as its
 * chunks in files of their own.
 */
@DisplayName("JfrRecordingEventParser over realistic recordings")
class JfrRecordingEventParserRealisticTest {

    /** High enough that nothing reaches it, so every source is split. */
    private static final int ALWAYS_SPLIT = Integer.MAX_VALUE;

    /** Low enough that one file is already enough, so every source is parsed as it lies. */
    private static final int AS_IT_LIES = 1;

    /** One of the JDK's events, which only its own chain records. */
    private static final String JDK_GC_EVENT = "jdk.GCPhaseParallel";

    private static final List<String> PROFILER_TYPES =
            List.of(EXECUTION_SAMPLE, WALL_CLOCK_SAMPLE, ALLOCATION_IN_NEW_TLAB, THREAD_PARK, MONITOR_ENTER);

    private static final List<String> SERVICE_TYPES = List.of(HTTP_SERVER_EXCHANGE, TRACE_SPAN, JDBC_QUERY,
            JDBC_UPDATE, JDBC_DELETE, CONNECTION_ACQUIRED, POOL_STATISTICS);

    @TempDir
    static Path work;

    /** Each reading parsed once for every test that asks for it: a parse of a whole run takes seconds. */
    private static final Map<String, EventTally> READINGS = new ConcurrentHashMap<>();

    @Nested
    @DisplayName("What a run holds")
    class WhatARunHolds {

        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        @DisplayName("reaches the writer, every sample and every one of the service's own events")
        void everyEventAndSampleReachesTheWriter(TestappRecording recording) {
            EventTally tally = plain(recording, ALWAYS_SPLIT);

            assertCounts(recording.counts(), tally);
            assertTraces(recording.traces(), tally);
        }
    }

    /** A recording held one way or another is the same recording: nothing joins its files. */
    @Nested
    @DisplayName("One run, however it is read")
    class HoweverRead {

        @Test
        @DisplayName("the archive reads as the file it compresses")
        void theArchiveReadsAsThePlainFile() {
            assertSame(plain(DIRECT, ALWAYS_SPLIT), archive(DIRECT));
        }

        @Test
        @DisplayName("splitting the file into units changes nothing about what is parsed")
        void splittingChangesNothing() {
            assertSame(plain(DIRECT, ALWAYS_SPLIT), plain(DIRECT, AS_IT_LIES));
        }

        @Test
        @DisplayName("its chunks as separate files hold what the file holds")
        void theChunksAsFilesHoldTheFile() {
            EventTally pieces = reading("pieces " + DIRECT, () -> new RecordingSources(chunkFiles(DIRECT)), ALWAYS_SPLIT);

            assertSame(plain(DIRECT, ALWAYS_SPLIT), pieces);
        }
    }

    /** What each chain contributes: the profiler's samples from one, the JDK's and the service's events from the other. */
    @Nested
    @DisplayName("Two chains in one file")
    class Chains {

        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        @DisplayName("the profiler's chunk alone holds every profiler event of the run, and none of the service's")
        void theProfilersChunkHoldsEveryProfilerEvent(TestappRecording recording) {
            EventTally profiler = reading("profiler chunk " + recording,
                    () -> RecordingSources.of(chunkFiles(recording).get(PROFILER_CHUNK)), ALWAYS_SPLIT);

            assertCounts(recording.counts(), profiler);
            for (String type : SERVICE_TYPES) {
                assertEquals(0, profiler.events(type), type);
            }
            assertEquals(0, profiler.events(JDK_GC_EVENT));
        }

        @ParameterizedTest
        @EnumSource(TestappRecording.class)
        @DisplayName("the JDK's chunks hold none of the profiler's events, and all of its own and the service's")
        void theJdksChunksHoldNoneOfTheProfilersEvents(TestappRecording recording) {
            List<Path> files = chunkFiles(recording);
            EventTally jdk = reading("jdk chunks " + recording,
                    () -> new RecordingSources(List.of(files.get(0), files.get(1))), ALWAYS_SPLIT);

            for (String type : PROFILER_TYPES) {
                assertEquals(0, jdk.events(type), type);
            }
            assertTraces(recording.traces(), jdk);
            assertTrue(jdk.events(JDK_GC_EVENT) > 0, "the JDK's chain holds the GC events");
            assertEquals(jdk.events(JDK_GC_EVENT), plain(recording, ALWAYS_SPLIT).events(JDK_GC_EVENT));
        }
    }

    /**
     * The traced checkout runs: one chunk each, the JDK's own recorder only, with the tracing library's
     * events — a request's root and its steps — beside the JDK's samples.
     */
    @Nested
    @DisplayName("A traced run")
    class TracedRun {

        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        @DisplayName("reaches the writer with every span, root and sample, however it is read")
        void everySpanRootAndSampleReachesTheWriter(CheckoutRecording recording) {
            Path file = work.resolve(recording.fileName());
            if (Files.notExists(file)) {
                write(file, recording.bytes());
            }
            EventTally split = reading("split " + recording, () -> RecordingSources.of(file), ALWAYS_SPLIT);
            EventTally asItLies = reading("as it lies " + recording, () -> RecordingSources.of(file), AS_IT_LIES);

            CheckoutRecording.Counts counts = recording.counts();
            assertEquals(counts.exchanges(), split.events(CheckoutRecording.HTTP_SERVER_EXCHANGE));
            assertEquals(counts.spans(), split.events(CheckoutRecording.TRACE_SPAN));
            assertEquals(counts.executionSamples(), split.events(CheckoutRecording.EXECUTION_SAMPLE));
            assertEquals(counts.allocationSamples(), split.events(CheckoutRecording.ALLOCATION_SAMPLE));
            assertEquals(counts.monitorEnters(), split.events(CheckoutRecording.MONITOR_ENTER));
            assertEquals(counts.exceptionThrows(), split.events(CheckoutRecording.EXCEPTION_THROW));
            assertEquals(split.events(), asItLies.events());
        }
    }

    private static void assertCounts(Counts counts, EventTally tally) {
        assertEquals(counts.executionSamples(), tally.events(EXECUTION_SAMPLE), EXECUTION_SAMPLE);
        assertEquals(counts.wallClockEvents(), tally.events(WALL_CLOCK_SAMPLE), WALL_CLOCK_SAMPLE + " events");
        assertEquals(counts.wallClockSamples(), tally.samples(WALL_CLOCK_SAMPLE), WALL_CLOCK_SAMPLE + " samples");
        assertEquals(counts.allocationSamples(), tally.events(ALLOCATION_IN_NEW_TLAB), ALLOCATION_IN_NEW_TLAB);
        assertEquals(counts.threadParks(), tally.events(THREAD_PARK), THREAD_PARK);
        assertEquals(counts.monitorEnters(), tally.events(MONITOR_ENTER), MONITOR_ENTER);
    }

    private static void assertTraces(Traces traces, EventTally tally) {
        assertEquals(traces.exchanges(), tally.events(HTTP_SERVER_EXCHANGE), HTTP_SERVER_EXCHANGE);
        assertEquals(traces.spans(), tally.events(TRACE_SPAN), TRACE_SPAN);
        assertEquals(traces.queries(), tally.events(JDBC_QUERY), JDBC_QUERY);
        assertEquals(traces.updates(), tally.events(JDBC_UPDATE), JDBC_UPDATE);
        assertEquals(traces.deletes(), tally.events(JDBC_DELETE), JDBC_DELETE);
        assertEquals(traces.connectionsAcquired(), tally.events(CONNECTION_ACQUIRED), CONNECTION_ACQUIRED);
        assertEquals(traces.poolStatistics(), tally.events(POOL_STATISTICS), POOL_STATISTICS);
    }

    /**
     * Two readings of {@link TestappRecording#DIRECT} agree on every type — how many events, how many
     * samples — and on the run's own figures: two readings that lost the same events would still
     * agree with each other.
     */
    private static void assertSame(EventTally expected, EventTally actual) {
        assertCounts(DIRECT.counts(), actual);
        assertTraces(DIRECT.traces(), actual);
        assertEquals(expected.events(), actual.events());
        assertEquals(expected.samples(), actual.samples());
    }

    /** The run compressed as the hub leaves it: an archive is always split, its decompression in the same pass. */
    private static EventTally archive(TestappRecording recording) {
        return reading("archive " + recording, () -> RecordingSources.of(archiveFile(recording)), ALWAYS_SPLIT);
    }

    private static EventTally plain(TestappRecording recording, int chunkSplitThreshold) {
        return reading("plain " + recording + " " + chunkSplitThreshold,
                () -> RecordingSources.of(plainFile(recording)), chunkSplitThreshold);
    }

    private static EventTally reading(String key, Supplier<RecordingSources> sources, int chunkSplitThreshold) {
        return READINGS.computeIfAbsent(key, _ -> {
            TempDirFactory scratch = TempDirFactory.of(work.resolve("scratch"));
            EventTally tally = new EventTally();
            new JfrRecordingEventParser(scratch, new Lz4Compressor(scratch), chunkSplitThreshold)
                    .start(tally, sources.get());
            return tally;
        });
    }

    private static Path plainFile(TestappRecording recording) {
        Path plain = work.resolve(recording + ".jfr");
        if (Files.notExists(plain)) {
            write(plain, recording.bytes());
        }
        return plain;
    }

    private static Path archiveFile(TestappRecording recording) {
        Path archive = work.resolve(recording + ".jfr.lz4");
        if (Files.notExists(archive)) {
            Lz4Compressor.compress(plainFile(recording), archive);
        }
        return archive;
    }

    /** Every chunk of the file as a file of its own, in the order the file holds them. */
    private static List<Path> chunkFiles(TestappRecording recording) {
        Path pieces = work.resolve("pieces-" + recording);
        if (Files.notExists(pieces)) {
            return JfrParser.disassemble(plainFile(recording), pieces);
        }
        try (var listed = Files.list(pieces)) {
            return listed.sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, byte[] bytes) {
        try {
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
