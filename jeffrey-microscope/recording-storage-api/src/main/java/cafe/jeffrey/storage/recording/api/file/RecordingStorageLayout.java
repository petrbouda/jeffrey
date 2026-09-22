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
