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

import java.nio.file.Path;

/**
 * How a recording's files are laid out in Microscope's recordings directory.
 *
 * <p>The directory is flat and shared by every recording, so each file is stored as
 * {@code <recordingId>-<name>}: the prefix keeps the directory unique across recordings and is not
 * part of the name the file is known by. Everything that writes a recording file and everything
 * that reads one back goes through here, because they have to agree exactly — a writer and a
 * reader that disagreed by one prefix would store files nothing could find again. Readers start
 * from the recording's file rows, never from a listing of the directory, so nothing has to tell
 * which recording a stored file belongs to by its name.
 */
public final class RecordingStorageLayout {

    /**
     * Between a recording's id and its file's own name.
     */
    private static final String SEPARATOR = "-";

    private RecordingStorageLayout() {
    }

    /**
     * Where the given file of the given recording sits.
     *
     * @param filename the name the file is known by, without the storage prefix
     */
    public static Path storagePath(Path recordingsDir, String recordingId, String filename) {
        return recordingsDir.resolve(recordingId + SEPARATOR + filename);
    }
}
