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
package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chunk lengths are the profiler's configuration, so every rule is checked at two of them. */
class SessionChunksTest {

    private static final Instant START = Instant.parse("2026-03-01T06:00:00Z");

    private static ChunkedSessions session(long minutes) {
        return ChunkedSessions.startingAt(START, Duration.ofMinutes(minutes));
    }

    @Nested
    class ChunkLength {

        @ParameterizedTest
        @ValueSource(longs = {5, 15})
        void isTheSpacingOfTheChunkStarts(long minutes) {
            SessionChunks chunks = SessionChunks.of(session(minutes).compressed(10, 20, 30, 40).build());

            assertEquals(Optional.of(Duration.ofMinutes(minutes)), chunks.chunkLength());
        }

        /** One missing chunk doubles one spacing; the median does not move. */
        @ParameterizedTest
        @ValueSource(longs = {5, 15})
        void staysPutWhenAChunkIsMissingInTheMiddle(long minutes) {
            RecordingSession whole = session(minutes).compressed(10, 20, 30, 40, 50).build();
            List<RepositoryFile> files = new ArrayList<>(whole.files());
            files.remove(2);
            RecordingSession gapped = new RecordingSession(whole.id(), whole.name(), whole.instanceId(),
                    whole.createdAt(), whole.finishedAt(), whole.status(), null, files, false);

            assertEquals(Optional.of(Duration.ofMinutes(minutes)), SessionChunks.of(gapped).chunkLength());
        }

        @ParameterizedTest
        @ValueSource(longs = {5, 15})
        void countsTheChunkStillOpen(long minutes) {
            SessionChunks chunks = SessionChunks.of(session(minutes).compressed(10).live().build());

            assertEquals(Optional.of(Duration.ofMinutes(minutes)), chunks.chunkLength());
        }

        @ParameterizedTest
        @ValueSource(longs = {5, 15})
        void aSoleFinishedChunkIsMeasuredByItsOwnSpan(long minutes) {
            SessionChunks chunks = SessionChunks.of(session(minutes).compressed(10).build());

            assertEquals(Optional.of(Duration.ofMinutes(minutes)), chunks.chunkLength());
        }
    }

    @Nested
    class Startup {

        @ParameterizedTest
        @ValueSource(longs = {5, 15})
        void isTheFirstChunk(long minutes) {
            SessionChunks.Startup startup = SessionChunks.of(session(minutes).compressed(10, 20, 30).build()).startup();

            assertEquals(ChunkedSessions.fileId(0),
                    assertInstanceOf(SessionChunks.Startup.Kept.class, startup).chunk().file().id());
        }

        /** A profiler that began a little after the session still recorded its startup. */
        @ParameterizedTest
        @ValueSource(longs = {5, 15})
        void isKeptWhenTheProfilerStartedLate(long minutes) {
            SessionChunks.Startup startup = SessionChunks.of(session(minutes)
                    .profilerLate(Duration.ofSeconds(20)).compressed(10, 20, 30).build()).startup();

            assertInstanceOf(SessionChunks.Startup.Kept.class, startup);
        }

        @ParameterizedTest
        @ValueSource(longs = {5, 15})
        void isGoneOnceTheCleanerRemovedTheFirstChunks(long minutes) {
            ChunkedSessions session = session(minutes).compressed(10, 20, 30, 40, 50).withoutOldest(3);

            SessionChunks.Startup.Gone gone = assertInstanceOf(SessionChunks.Startup.Gone.class,
                    SessionChunks.of(session.build()).startup());

            assertEquals(ChunkedSessions.fileId(3), gone.oldest().file().id());
            assertEquals(Duration.ofMinutes(minutes), gone.chunkLength());
        }

        /** One chunk length after the start is still the first chunk; a millisecond more is not. */
        @ParameterizedTest
        @ValueSource(longs = {5, 15})
        void theBoundaryIsOneChunkLength(long minutes) {
            Duration length = Duration.ofMinutes(minutes);
            assertInstanceOf(SessionChunks.Startup.Kept.class, SessionChunks.of(session(minutes)
                    .profilerLate(length).compressed(10, 20, 30).build()).startup());
            assertInstanceOf(SessionChunks.Startup.Gone.class, SessionChunks.of(session(minutes)
                    .profilerLate(length.plusMillis(1)).compressed(10, 20, 30).build()).startup());
        }

        @Test
        void cannotBeToldWithoutARecordedStart() {
            RecordingSession whole = session(15).compressed(10, 20).build();
            RecordingSession noStart = new RecordingSession(whole.id(), whole.name(), whole.instanceId(), null,
                    whole.finishedAt(), RecordingStatus.FINISHED, null, whole.files(), false);

            assertInstanceOf(SessionChunks.Startup.Unknown.class, SessionChunks.of(noStart).startup());
        }
    }

    @Nested
    class Latest {

        @ParameterizedTest
        @ValueSource(longs = {5, 15})
        void isTheNewestFinishedChunkNeverTheOpenOne(long minutes) {
            SessionChunks chunks = SessionChunks.of(session(minutes).compressed(10, 20, 30).live().build());

            assertEquals(ChunkedSessions.fileId(2), chunks.latest().orElseThrow().file().id());
        }

        /** A chunk left empty by a killed JVM holds nothing to download. */
        @Test
        void skipsAnEmptyChunk() {
            SessionChunks chunks = SessionChunks.of(session(15).compressed(10, 20).raw(0).build());

            assertEquals(ChunkedSessions.fileId(1), chunks.latest().orElseThrow().file().id());
        }
    }

    @Nested
    class Peak {

        @ParameterizedTest
        @ValueSource(longs = {5, 15})
        void isTheLargestCompressedChunkWithTheMedianItWasComparedWith(long minutes) {
            SessionChunks.Peak peak = SessionChunks.of(session(minutes).compressed(10, 40, 20, 30).build())
                    .peak().orElseThrow();

            assertEquals(ChunkedSessions.fileId(1), peak.chunk().file().id());
            assertEquals(20, peak.medianSizeBytes());
            assertEquals(4, peak.compared());
            assertFalse(peak.tied());
            assertEquals(0, peak.notCompressed());
        }

        /** Size on disk compares only within one format: a chunk not compressed yet is left out. */
        @Test
        void leavesOutChunksNotCompressedYet() {
            SessionChunks.Peak peak = SessionChunks.of(session(15).compressed(10, 30, 20).raw(500).build())
                    .peak().orElseThrow();

            assertEquals(ChunkedSessions.fileId(1), peak.chunk().file().id());
            assertEquals(1, peak.notCompressed());
        }

        @Test
        void aTieTakesTheMostRecent() {
            SessionChunks.Peak peak = SessionChunks.of(session(15).compressed(30, 10, 30).build()).peak().orElseThrow();

            assertEquals(ChunkedSessions.fileId(2), peak.chunk().file().id());
            assertTrue(peak.tied());
        }

        @Test
        void isAbsentWhenNothingIsCompressed() {
            assertTrue(SessionChunks.of(session(15).raw(10, 20).build()).peak().isEmpty());
        }
    }
}
