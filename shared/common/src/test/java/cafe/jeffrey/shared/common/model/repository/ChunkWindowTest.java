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

package cafe.jeffrey.shared.common.model.repository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkWindowTest {

    private static final Instant T0 = Instant.parse("2026-09-14T10:00:00Z");

    private static Instant minutes(long minutes) {
        return T0.plusSeconds(minutes * 60);
    }

    private static RepositoryFile chunk(String id, long startMinute) {
        return new RepositoryFile(id, "profile-" + id + ".jfr", minutes(startMinute), 10L,
                SupportedRecordingFile.JFR, RecordingStatus.FINISHED, null);
    }

    private static RepositoryFile log(long startMinute) {
        return new RepositoryFile("log", "app.log", minutes(startMinute), 10L,
                SupportedRecordingFile.APP_LOG, RecordingStatus.FINISHED, null);
    }

    /** Four chunks of ten minutes, the session finished at +40. */
    private static final List<RepositoryFile> CHUNKS = List.of(
            chunk("c3", 30), chunk("c1", 10), chunk("c0", 0), chunk("c2", 20), log(0));

    @Nested
    class Bounds {

        @Test
        void needsAtLeastOneBound() {
            assertThrows(IllegalArgumentException.class, () -> new ChunkWindow(null, null));
        }

        @Test
        void startMustPrecedeEnd() {
            assertThrows(IllegalArgumentException.class, () -> new ChunkWindow(minutes(5), minutes(5)));
            assertThrows(IllegalArgumentException.class, () -> new ChunkWindow(minutes(6), minutes(5)));
        }

        @Test
        void epochMillisMapToInstants() {
            ChunkWindow window = ChunkWindow.ofEpochMillis(minutes(1).toEpochMilli(), null);
            assertEquals(minutes(1), window.start());
            assertNull(window.end());
        }
    }

    @Nested
    class Selecting {

        @Test
        void takesTheChunksWhoseSpanTouchesTheWindowOldestFirst() {
            ChunkWindow.Selection selection = new ChunkWindow(minutes(15), minutes(25)).select(CHUNKS, minutes(40));

            assertEquals(List.of("c1", "c2"), selection.fileIds());
            assertEquals(minutes(10), selection.coverageStart());
            assertEquals(minutes(30), selection.coverageEnd());
        }

        @Test
        void aChunkStartingExactlyAtTheEndIsNotTaken() {
            ChunkWindow.Selection selection = new ChunkWindow(minutes(5), minutes(20)).select(CHUNKS, minutes(40));

            assertEquals(List.of("c0", "c1"), selection.fileIds());
        }

        @Test
        void aChunkEndingExactlyAtTheStartIsNotTaken() {
            ChunkWindow.Selection selection = new ChunkWindow(minutes(20), minutes(35)).select(CHUNKS, minutes(40));

            assertEquals(List.of("c2", "c3"), selection.fileIds());
        }

        @Test
        void anOpenStartReachesBackToTheFirstChunk() {
            ChunkWindow.Selection selection = new ChunkWindow(null, minutes(12)).select(CHUNKS, minutes(40));

            assertEquals(List.of("c0", "c1"), selection.fileIds());
        }

        @Test
        void anOpenEndReachesForwardToTheLastChunk() {
            ChunkWindow.Selection selection = new ChunkWindow(minutes(28), null).select(CHUNKS, minutes(40));

            assertEquals(List.of("c2", "c3"), selection.fileIds());
            assertEquals(minutes(40), selection.coverageEnd());
        }

        @Test
        void theLastChunkOfARunningSessionIsOpenEnded() {
            ChunkWindow.Selection selection = new ChunkWindow(minutes(100), minutes(200)).select(CHUNKS, null);

            assertEquals(List.of("c3"), selection.fileIds());
            assertNull(selection.coverageEnd());
        }

        @Test
        void aWindowOutsideAFinishedSessionSelectsNothing() {
            ChunkWindow.Selection selection = new ChunkWindow(minutes(100), minutes(200)).select(CHUNKS, minutes(40));

            assertTrue(selection.isEmpty());
            assertNull(selection.coverageStart());
        }

        @Test
        void namedChunksCoverTheSpanBetweenTheirStartsAndTheNextOne() {
            ChunkWindow.Selection selection = ChunkWindow.ofFiles(CHUNKS, Set.of("c1", "c3", "log"), minutes(40));

            assertEquals(List.of("c1", "c3"), selection.fileIds());
            assertEquals(minutes(10), selection.coverageStart());
            assertEquals(minutes(40), selection.coverageEnd());
            assertFalse(selection.isWholeSession(CHUNKS));
        }

        @Test
        void everyFinishedChunkIsTheWholeSession() {
            ChunkWindow.Selection selection = ChunkWindow.ofFiles(CHUNKS, Set.of("c0", "c1", "c2", "c3"), minutes(40));

            assertTrue(selection.isWholeSession(CHUNKS));
        }

        @Test
        void ignoresFilesThatAreNotFinishedChunks() {
            RepositoryFile open = new RepositoryFile("c4", "profile-c4.jfr", minutes(40), 10L,
                    SupportedRecordingFile.JFR, RecordingStatus.ACTIVE, null);
            List<RepositoryFile> files = List.of(chunk("c0", 0), log(0), open);

            ChunkWindow.Selection selection = new ChunkWindow(minutes(0), minutes(60)).select(files, null);

            assertEquals(List.of("c0"), selection.fileIds());
        }
    }
}
