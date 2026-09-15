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

package cafe.jeffrey.heartbeat;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Writes the two liveness files, each through a temporary file and a rename.
 *
 * <p>The rename is what makes the contract safe to read without locking. A hub polling the shared
 * volume must never see a half-written timestamp, and it never does: it sees either the previous
 * file or the new one. Where the filesystem cannot rename atomically — some network mounts — the
 * plain replace is used instead, which is the best available and still far narrower than writing
 * into the file in place.</p>
 */
final class HeartbeatWriter {

    private static final String TEMPORARY_SUFFIX = ".tmp";

    private final Path heartbeatFile;
    private final Path heartbeatTemporaryFile;
    private final Path finishedFile;
    private final Path finishedTemporaryFile;

    HeartbeatWriter(Path directory) {
        this.heartbeatFile = directory.resolve(HeartbeatFiles.HEARTBEAT_FILE);
        this.heartbeatTemporaryFile = directory.resolve(HeartbeatFiles.HEARTBEAT_FILE + TEMPORARY_SUFFIX);
        this.finishedFile = directory.resolve(HeartbeatFiles.FINISHED_FILE);
        this.finishedTemporaryFile = directory.resolve(HeartbeatFiles.FINISHED_FILE + TEMPORARY_SUFFIX);
    }

    void beat(long epochMillis) throws IOException {
        write(heartbeatTemporaryFile, heartbeatFile, epochMillis);
    }

    void finish(long epochMillis) throws IOException {
        write(finishedTemporaryFile, finishedFile, epochMillis);
    }

    /** Best-effort removal of the scratch file; leaving one behind is untidy, never harmful. */
    void discardTemporaryFiles() {
        try {
            Files.deleteIfExists(heartbeatTemporaryFile);
        } catch (IOException e) {
            // nothing to do about it, and nothing depends on it
        }
    }

    private static void write(Path temporaryFile, Path targetFile, long epochMillis) throws IOException {
        Files.writeString(temporaryFile, Long.toString(epochMillis));
        try {
            Files.move(temporaryFile, targetFile, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporaryFile, targetFile, REPLACE_EXISTING);
        }
    }
}
