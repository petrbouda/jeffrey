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
