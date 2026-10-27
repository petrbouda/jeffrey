/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.model.repository;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A recording session as the volume reports it. A listing carries the session's {@link SessionSize}
 * but none of its files; only a single-session read carries the files, so every method below
 * that reads {@code files} answers only for a session read on its own.
 *
 * @param lastDataAt when its recorded data ends ({@link RecordedSpan#end}), or {@code null} while it
 *                   has no recording
 * @param newest     whether it is its instance's newest session: no newer one has started there, so
 *                   it may still grow and its newest recording may still be on its way onto the volume
 * @param failed     a newer session ended it before it produced any data, as judged from the files on
 *                   the volume
 * @param size       its file count and bytes, measured from {@code files} when not given
 */
public record RecordingSession(
        String id,
        String name,
        String instanceId,
        Instant createdAt,
        Instant lastDataAt,
        boolean newest,
        Path absolutePath,
        List<RepositoryFile> files,
        boolean failed,
        SessionSize size) {

    /**
     * A session loaded without its files holds an empty list rather than none, so every reader
     * below can ask it about its files without first asking whether it has any.
     */
    public RecordingSession {
        files = files == null ? List.of() : files;
        Objects.requireNonNull(size, "size");
    }

    /**
     * A session read with its files, its size measured from them.
     */
    public RecordingSession(
            String id,
            String name,
            String instanceId,
            Instant createdAt,
            Instant lastDataAt,
            boolean newest,
            Path absolutePath,
            List<RepositoryFile> files,
            boolean failed) {

        this(id, name, instanceId, createdAt, lastDataAt, newest, absolutePath, files, failed,
                SessionSize.of(files == null ? List.of() : files));
    }

    /**
     * Total size in bytes of every file in this session, whether or not the files came with it.
     */
    public long totalSizeBytes() {
        return size.bytes();
    }

    /**
     * Number of files in this session, whether or not the files came with it.
     */
    public int fileCount() {
        return size.fileCount();
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
     * <p>The hub never compresses or trims this file. Microscope lists it and hands it over; a
     * download that arrives incomplete is refused by the chunk check on arrival instead. Only
     * meaningful when the session was loaded WITH files.
     */
    public Optional<RepositoryFile> openRecording() {
        if (!newest) {
            return Optional.empty();
        }
        return recordings().stream().reduce((older, newer) -> newer);
    }

    /**
     * Whether this file is the recording that may still be on its way onto the volume.
     */
    public boolean isOpen(RepositoryFile file) {
        return openRecording().filter(open -> open.equals(file)).isPresent();
    }

    /**
     * The session's recording chunks, oldest first. One definition, because a reader that counted
     * chunks differently from the way it picked them would call a whole session a part of itself.
     */
    public List<RepositoryFile> recordings() {
        return files.stream()
                .filter(RepositoryFile::isRecordingFile)
                .filter(file -> file.createdAt() != null)
                .sorted(Comparator.comparing(RepositoryFile::createdAt))
                .toList();
    }

    /** When each recording started, oldest first — what its span is measured from. */
    public List<Instant> recordingStarts() {
        return recordings().stream().map(RepositoryFile::createdAt).toList();
    }
}
