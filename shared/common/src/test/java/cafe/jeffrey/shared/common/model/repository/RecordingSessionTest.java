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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingSessionTest {

    private static final Instant CREATED_AT = Instant.parse("2026-02-20T12:00:00Z");
    private static final Instant FINISHED_AT = CREATED_AT.plusSeconds(60);

    private static RepositoryFile file(Long size) {
        return new RepositoryFile(
                "file-1", "file-1", CREATED_AT, size,
                SupportedRecordingFile.JFR, RecordingStatus.FINISHED, null);
    }

    private static RecordingSession session(Instant finishedAt, RepositoryFile... files) {
        RecordingStatus status = finishedAt != null ? RecordingStatus.FINISHED : RecordingStatus.ACTIVE;
        return new RecordingSession(
                "session-1", "session-1", "inst-1", CREATED_AT, finishedAt,
                status, null, null, List.of(files), false);
    }

    @Nested
    class TotalSizeBytes {

        @Test
        void sumsFileSizesTreatingUnknownAsZero() {
            RecordingSession recordingSession = session(FINISHED_AT, file(100L), file(null), file(50L));

            assertEquals(150L, recordingSession.totalSizeBytes());
        }
    }

    @Nested
    class IsFailedEmpty {

        @Test
        void finishedSessionWithoutFilesIsFailed() {
            assertTrue(session(FINISHED_AT).isFailedEmpty());
        }

        @Test
        void finishedSessionWithOnlyZeroSizeFilesIsFailed() {
            assertTrue(session(FINISHED_AT, file(0L), file(null)).isFailedEmpty());
        }

        @Test
        void finishedSessionWithDataIsNotFailed() {
            assertFalse(session(FINISHED_AT, file(1L)).isFailedEmpty());
        }

        @Test
        void activeSessionWithZeroBytesIsNotFailed() {
            assertFalse(session(null).isFailedEmpty());
        }
    }
}
