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

package cafe.jeffrey.hub.model.repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.Optional;

/**
 * A session as the volume says it is at the moment of reading.
 *
 * @param newest whether it is its instance's newest session: no newer one has started there, so
 *               its newest recording may still be on its way onto the volume
 */
public record RecordingSession(
        String id,
        String name,
        String instanceId,
        Instant createdAt,
        boolean newest,
        List<RepositoryFile> files) {

    /**
     * A session loaded without its files holds an empty list rather than none, so every reader
     * below can ask it about its files without first asking whether it has any.
     */
    private static final Comparator<RepositoryFile> BY_START = Comparator.comparing(RepositoryFile::createdAt);

    public RecordingSession {
        files = files == null ? List.of() : files;
    }

    /**
     * Total size in bytes of every file in this session. Zero when the session was
     * loaded without files.
     */
    public long totalSizeBytes() {
        return files.stream()
                .mapToLong(RepositoryFile::size)
                .sum();
    }

    /**
     * The one recording that may still be on its way onto the volume, or empty when there is none.
     *
     * <p>This is the whole of what "a file's status" ever meant, and it is a fact about the
     * session's place in its instance rather than about any file: the profiler writes each
     * recording whole when its loop ends, so a file can be caught mid-write only while it is the
     * newest recording of the instance's newest session. A session with a newer one after it holds
     * none, and neither does a newest session that has written no recording yet.
     *
     * <p>Newest by {@link RepositoryFile#createdAt()} — the timestamp the profiler put in the
     * file's own name — and emphatically not by position in a listing, which is sorted for
     * presentation, nor by the file being uncompressed, which only says the hub has not reached
     * it yet. Every other reader of these files orders them by that timestamp, and a second
     * notion of "newest" in the same tree is what let the two disagree.
     *
     * <p>Load-bearing: the compression job refuses to touch this file and both retention rules
     * that trim files refuse to delete it. Only meaningful when the session was loaded WITH files.
     */
    public Optional<RepositoryFile> openRecording() {
        if (!newest) {
            return Optional.empty();
        }
        return recordings().max(BY_START);
    }

    /**
     * The session's recordings a trim or the quota may delete, oldest first: every one but the
     * newest-file guard's ({@link #openRecording()}). One definition, because a reader that counted
     * chunks differently from the way it picked them would call a whole session a part of itself.
     */
    public List<RepositoryFile> trimmableRecordings() {
        Optional<RepositoryFile> open = openRecording();
        return recordings()
                .filter(file -> open.filter(file::equals).isEmpty())
                .sorted(BY_START)
                .toList();
    }

    /**
     * When the session's newest recording started — the timestamp in its name — or the session's
     * own start while it has none. What retention measures a session's age by: a running JVM's
     * profiler loops, so its session always has a recent one. Microscope's "last data" goes one
     * measured chunk further; retention needs no more than the start. Only meaningful when the
     * session was loaded WITH files.
     */
    public Instant lastRecordingStart() {
        return recordings()
                .map(RepositoryFile::createdAt)
                .max(Comparator.naturalOrder())
                .orElse(createdAt);
    }

    /** The session's recordings that carry a start: the files every rule above reads. */
    private Stream<RepositoryFile> recordings() {
        return files.stream()
                .filter(RepositoryFile::isRecordingFile)
                .filter(file -> file.createdAt() != null);
    }

    /**
     * A session that produced no data at all before a newer one replaced it — typically a
     * prematurely killed process (OOM kill, container healthcheck restart loop). The newest session
     * is never failed: it may simply not have written anything yet. Only meaningful when the
     * session was loaded WITH files (listSessions(true)) — a session loaded without files always
     * reports zero size and would be misclassified as failed.
     */
    public boolean isFailedEmpty() {
        return !newest && totalSizeBytes() == 0L;
    }
}
