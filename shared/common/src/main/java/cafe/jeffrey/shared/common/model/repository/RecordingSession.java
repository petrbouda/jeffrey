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
import java.time.Duration;
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
        Path absoluteStreamingPath,
        List<RepositoryFile> files,
        boolean retained) {

    /**
     * How long a session may have lived and still read as a crash. A container that
     * crash-loops dies in seconds, so this is generous rather than tight; what it bounds is
     * the window in which zero bytes on disk is believable evidence that the process never
     * got far enough to write.
     */
    private static final Duration CRASHED_SESSION_LIFETIME = Duration.ofMinutes(1);

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
     *
     * <p>Zero bytes alone is not the evidence, because the sizes are read straight off the
     * repository directory and a filesystem can lie about them: an object-storage FUSE mount
     * or an NFS mount with attribute caching serves each file's size as of when it was
     * created, so a session that is recording perfectly well reports nothing but zeros for as
     * long as the cache holds. Being wrong here is expensive — a failed-empty session forfeits
     * the keep-newest protection slot in {@code ProjectInstanceSessionCleanerJob} and is
     * collapsed as a crash in the UI — so a zero is only believed where a crash actually
     * explains it.
     *
     * <p>Two cases do explain it. A session that produced <em>no files at all</em> is empty by
     * count rather than by size, and no stat can have gone stale. A session that holds files
     * which all measure zero is a crash only while it died too early to have written anything;
     * past {@link #CRASHED_SESSION_LIFETIME} a JVM that ran that long and produced not one byte
     * is far better explained by the sizes than by the process.
     */
    public boolean isFailedEmpty() {
        if (finishedAt == null || totalSizeBytes() != 0L) {
            return false;
        }
        return files.isEmpty() || diedBeforeWritingAnything();
    }

    private boolean diedBeforeWritingAnything() {
        if (createdAt == null) {
            return true;
        }
        return Duration.between(createdAt, finishedAt).compareTo(CRASHED_SESSION_LIFETIME) < 0;
    }
}

