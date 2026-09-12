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

package cafe.jeffrey.hub.core.project.repository;

import cafe.jeffrey.shared.common.filesystem.FileSizeReader;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class AsprofFileRepositoryStorageTest {

    /**
     * Which files cost a round trip to the share. Every open here is paid per file, per session,
     * on every listing of the project, so the cases that read from the listing are as much the
     * contract as the case that does not.
     */
    @Nested
    class SizeReader {

        @Test
        void opensTheRecordingOfASessionThatIsStillRecording() {
            assertSame(FileSizeReader.OPEN_HANDLE,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedRecordingFile.JFR));
        }

        @Test
        void opensTheLogsAndCachesOfASessionThatIsStillRecording() {
            assertSame(FileSizeReader.OPEN_HANDLE,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedRecordingFile.JVM_LOG));
            assertSame(FileSizeReader.OPEN_HANDLE,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedRecordingFile.ASPROF_TEMP));
        }

        @Test
        void readsACompressedRecordingFromTheListingEvenWhileTheSessionRecords() {
            // This hub wrote and closed it, so no other client holds it open and the listing is
            // final. A long session accumulates one of these every chunk, and opening each would
            // grow the cost of a listing without bound.
            assertSame(FileSizeReader.FILE_ATTRIBUTES,
                    AsprofFileRepositoryStorage.sizeReader(RecordingStatus.ACTIVE, SupportedRecordingFile.JFR_LZ4));
        }

        @Test
        void readsEveryFileOfAFinishedSessionFromTheListing() {
            for (SupportedRecordingFile fileType : SupportedRecordingFile.values()) {
                assertSame(FileSizeReader.FILE_ATTRIBUTES,
                        AsprofFileRepositoryStorage.sizeReader(RecordingStatus.FINISHED, fileType),
                        "file type: " + fileType);
            }
        }
    }
}
