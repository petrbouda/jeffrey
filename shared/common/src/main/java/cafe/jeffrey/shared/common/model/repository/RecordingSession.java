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

package cafe.jeffrey.shared.common.model.repository;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RecordingSession(
        String id,
        String name,
        String instanceId,
        Instant createdAt,
        Instant finishedAt,
        RecordingStatus status,
        Path absolutePath,
        List<RepositoryFile> files,
        boolean retained) {

    /**
     * Total size in bytes of every file in this session. Zero when the session was
     * loaded without files. Files whose size could not be determined count as zero
     * rather than failing the whole sum.
     */
    public long totalSizeBytes() {
        return files.stream()
                .map(RepositoryFile::size)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }

    /**
     * A finished session that produced no data at all — typically a prematurely killed
     * process (OOM kill, container healthcheck restart loop). Only meaningful when the
     * session was loaded WITH files (listSessions(true)) — a session loaded without
     * files always reports zero size and would be misclassified as failed.
     */
    public boolean isFailedEmpty() {
        return finishedAt != null && totalSizeBytes() == 0L;
    }

    /**
     * The finished chunks of this session's recording, oldest first and one per id — the files
     * the hub compresses, replays and trims, and a client assembles into the recording a profile
     * is built from. Every consumer of "the session's JFR" goes through here rather than
     * filtering the file list itself; the rules are {@link RecordingChunks}'.
     */
    public List<RepositoryFile> finishedChunks() {
        return RecordingChunks.finished(files);
    }
}

