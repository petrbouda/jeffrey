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

package cafe.jeffrey.storage.recording.api.file;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordingStorageLayoutTest {

    private static final Path RECORDINGS_DIR = Path.of("/data/recordings");
    private static final String RECORDING_ID = "rec-1";

    @Nested
    class StoragePath {

        @Test
        void prefixesTheNameWithTheRecordingId() {
            assertEquals(
                    RECORDINGS_DIR.resolve("rec-1-app.jfr"),
                    RecordingStorageLayout.storagePath(RECORDINGS_DIR, RECORDING_ID, "app.jfr"));
        }
    }
}
