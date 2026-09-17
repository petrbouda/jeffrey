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

package cafe.jeffrey.microscope.model.repository;

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
     * A session loaded without its files holds an empty list rather than none, so every reader
     * below can ask it about its files without first asking whether it has any.
     */
    public RecordingSession {
        files = files == null ? List.of() : files;
    }

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
     * The one chunk the profiler is still writing, or empty when there is none.
     *
     * <p>This is the whole of what "a file's status" ever meant, and it is a fact about the
     * session rather than about any file: a session that is still recording holds exactly one
     * chunk open, and it is the newest one. A finished session holds none, and neither does a
     * live session that has not opened a chunk yet.
     *
     * <p>Newest by {@link RepositoryFile#createdAt()} — the timestamp the profiler put in the
     * file's own name — and emphatically not by position in a listing, which is sorted for
     * presentation, nor by the file being uncompressed, which only says the hub has not reached
     * it yet. Every other reader of these files orders them by that timestamp, and a second
     * notion of "newest" in the same tree is what let the two disagree.
     *
     * <p>Load-bearing: the compression job refuses to touch this file, both retention jobs
     * refuse to delete it, no download may include it, and reading it is what produces a
     * truncated answer. Only meaningful when the session was loaded WITH files.
     */
    public Optional<RepositoryFile> openRecording() {
        if (status == RecordingStatus.FINISHED) {
            return Optional.empty();
        }
        return files.stream()
                .filter(RepositoryFile::isRecordingFile)
                .filter(file -> file.createdAt() != null)
                .max(Comparator.comparing(RepositoryFile::createdAt));
    }

    /**
     * Whether this file is the chunk the profiler is still writing.
     */
    public boolean isOpen(RepositoryFile file) {
        return openRecording().filter(open -> open.equals(file)).isPresent();
    }

    /**
     * Every file of the session except the chunk still being written — the ones whose bytes are
     * final and may therefore be compressed, deleted, downloaded or read.
     */
    public List<RepositoryFile> finishedFiles() {
        Optional<RepositoryFile> open = openRecording();
        return files.stream()
                .filter(file -> open.filter(file::equals).isEmpty())
                .toList();
    }

    /**
     * The session's closed recording chunks, oldest first. One definition, because a reader that
     * counted chunks differently from the way it picked them would call a whole session a part of
     * itself.
     */
    public List<RepositoryFile> finishedRecordings() {
        Optional<RepositoryFile> open = openRecording();
        return files.stream()
                .filter(RepositoryFile::isRecordingFile)
                .filter(file -> file.createdAt() != null)
                .filter(file -> open.filter(file::equals).isEmpty())
                .sorted(Comparator.comparing(RepositoryFile::createdAt))
                .toList();
    }

    /**
     * The newest recording chunk the profiler has closed, or empty when it has not closed one
     * yet. This is the chunk that carries the session's one-shot configuration events, and —
     * once the session is finished — its {@code jdk.Shutdown}.
     */
    public Optional<RepositoryFile> latestFinishedRecording() {
        List<RepositoryFile> finished = finishedRecordings();
        return finished.isEmpty() ? Optional.empty() : Optional.of(finished.getLast());
    }
}

