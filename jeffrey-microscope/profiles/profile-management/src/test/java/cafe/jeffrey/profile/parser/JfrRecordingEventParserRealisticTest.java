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
import cafe.jeffrey.test.recording.PersonsRecording;
import cafe.jeffrey.test.recording.PersonsRecording.Counts;
import cafe.jeffrey.test.recording.PersonsRecording.ProfilerChunk;
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

import static cafe.jeffrey.test.recording.PersonsRecording.ALLOCATION_IN_NEW_TLAB;
import static cafe.jeffrey.test.recording.PersonsRecording.DIRECT;
import static cafe.jeffrey.test.recording.PersonsRecording.DOM;
import static cafe.jeffrey.test.recording.PersonsRecording.EXECUTION_SAMPLE;
import static cafe.jeffrey.test.recording.PersonsRecording.MONITOR_ENTER;
import static cafe.jeffrey.test.recording.PersonsRecording.THREAD_PARK;
import static cafe.jeffrey.test.recording.PersonsRecording.WALL_CLOCK_SAMPLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The event parser over realistic recordings: files a profiler with {@code jfrsync} wrote, each two
 * chains of chunks overlapping in time — the JDK's own and async-profiler's — whose event types are
 * numbered differently, kept LZ4-compressed as the hub leaves them. What reaches the writer is held
 * to what {@link PersonsRecording} pins, however the file is read.
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

    @TempDir
    static Path work;

    /** Each reading parsed once for every test that asks for it: a parse of a whole run takes seconds. */
    private static final Map<String, EventTally> READINGS = new ConcurrentHashMap<>();

    @Nested
    @DisplayName("What a run holds")
    class WhatARunHolds {

        @ParameterizedTest
        @EnumSource(PersonsRecording.class)
        @DisplayName("reaches the writer, every event and every sample")
        void everyEventAndSampleReachesTheWriter(PersonsRecording recording) {
            EventTally tally = archive(recording);

            assertCounts(recording.counts(), tally);
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

    /** What each chain contributes: the profiler's samples from one, the JDK's events from the other. */
    @Nested
    @DisplayName("Two chains in one file")
    class Chains {

        @Test
        @DisplayName("the profiler's chunks alone hold every profiler event of the run")
        void theProfilersChunksHoldEveryProfilerEvent() {
            EventTally profiler = reading("profiler chunks " + DOM,
                    () -> new RecordingSources(profilerChunkFiles(DOM)), ALWAYS_SPLIT);

            assertCounts(DOM.counts(), profiler);
            assertEquals(0, profiler.events(JDK_GC_EVENT));
        }

        @ParameterizedTest
        @EnumSource(PersonsRecording.class)
        @DisplayName("each of the profiler's chunks holds its own share")
        void eachProfilerChunkHoldsItsShare(PersonsRecording recording) {
            List<Path> files = chunkFiles(recording);
            for (ProfilerChunk chunk : recording.profilerChunks()) {
                EventTally alone = reading("chunk " + chunk.index() + " " + recording,
                        () -> RecordingSources.of(files.get(chunk.index())), ALWAYS_SPLIT);

                assertCounts(chunk.counts(), alone);
            }
        }

        @Test
        @DisplayName("the JDK's chunks hold none of the profiler's events, and all of its own")
        void theJdksChunksHoldNoneOfTheProfilersEvents() {
            List<Path> files = chunkFiles(DOM);
            EventTally jdk = reading("jdk chunks " + DOM,
                    () -> new RecordingSources(List.of(files.get(0), files.get(1))), ALWAYS_SPLIT);

            for (String type : PROFILER_TYPES) {
                assertEquals(0, jdk.events(type), type);
            }
            assertTrue(jdk.events(JDK_GC_EVENT) > 0, "the JDK's chain holds the GC events");
            assertEquals(jdk.events(JDK_GC_EVENT), archive(DOM).events(JDK_GC_EVENT));
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

    /**
     * Two readings of {@link PersonsRecording#DIRECT} agree on every type — how many events, how many
     * samples — and on the run's own figures: two readings that lost the same events would still
     * agree with each other.
     */
    private static void assertSame(EventTally expected, EventTally actual) {
        assertCounts(DIRECT.counts(), actual);
        assertEquals(expected.events(), actual.events());
        assertEquals(expected.samples(), actual.samples());
    }

    /** The run as kept, compressed: an archive is always split, its decompression in the same pass. */
    private static EventTally archive(PersonsRecording recording) {
        return reading("archive " + recording, () -> RecordingSources.of(archiveFile(recording)), ALWAYS_SPLIT);
    }

    private static EventTally plain(PersonsRecording recording, int chunkSplitThreshold) {
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

    private static Path archiveFile(PersonsRecording recording) {
        Path archive = work.resolve(recording.fileName());
        if (Files.notExists(archive)) {
            write(archive, recording.bytes());
        }
        return archive;
    }

    private static Path plainFile(PersonsRecording recording) {
        Path plain = work.resolve(recording + ".jfr");
        if (Files.notExists(plain)) {
            Lz4Compressor.decompress(archiveFile(recording), plain);
        }
        return plain;
    }

    /** Every chunk of the file as a file of its own, in the order the file holds them. */
    private static List<Path> chunkFiles(PersonsRecording recording) {
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

    private static List<Path> profilerChunkFiles(PersonsRecording recording) {
        List<Path> files = chunkFiles(recording);
        return recording.profilerChunks().stream().map(chunk -> files.get(chunk.index())).toList();
    }

    private static void write(Path file, byte[] bytes) {
        try {
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
