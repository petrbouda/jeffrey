/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.shared.common.model;

import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.repository.SupportedFile;

import java.nio.file.Path;
import java.time.Instant;

public record RecordingFile(
        String id,
        String recordingId,
        String filename,
        SupportedFile recordingFileType,
        Instant uploadedAt,
        long sizeInBytes) {

    /**
     * Whether a profile is parsed from this file — see {@link SupportedFile#isProfileRecording()}.
     */
    public boolean isRecordingFile() {
        return recordingFileType.isProfileRecording();
    }

    /**
     * The name a chunk keeps across compression: {@code profile-1.jfr} and {@code profile-1.jfr.lz4}
     * read the same, so a set of chunks orders the same way whichever form each is in.
     */
    public String nameWithoutChunkExtension() {
        return FileSystemUtils.removeExtension(Path.of(filename), SupportedFile.recordingChunkExtensions());
    }
}
