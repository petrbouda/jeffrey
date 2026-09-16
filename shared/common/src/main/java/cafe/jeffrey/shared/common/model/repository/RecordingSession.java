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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
     * The newest recording chunk the profiler has closed, or empty when it has not closed one
     * yet. This is the chunk that carries the session's one-shot configuration events, and —
     * once the session is finished — its {@code jdk.Shutdown}.
     *
     * <p>The chunk still being written is excluded by its status rather than by its position:
     * a live session's newest chunk is open, and reading one is what produces a truncated
     * answer. Only meaningful when the session was loaded WITH files.
     */
    public Optional<RepositoryFile> latestFinishedRecording() {
        return files.stream()
                .filter(RepositoryFile::isRecordingFile)
                .filter(file -> file.status() == RecordingStatus.FINISHED)
                .max(Comparator.comparing(
                        RepositoryFile::createdAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
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
}

