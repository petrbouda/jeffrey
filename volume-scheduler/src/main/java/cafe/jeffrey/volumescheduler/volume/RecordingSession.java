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

package cafe.jeffrey.volumescheduler.volume;

import cafe.jeffrey.shared.common.JeffreyLayout;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A session as the volume says it is at the moment of reading, with its files.
 *
 * @param newest whether it is its instance's newest session: no newer one has started there, so
 *               its newest recording may still be on its way onto the volume
 */
public record RecordingSession(
        String id,
        String instanceId,
        Instant createdAt,
        boolean newest,
        List<RecordingSessionFile> files) {

    /** The order of a session's chunks: the one rule both sides keep, in {@link JeffreyLayout}. */
    private static final Comparator<RecordingSessionFile> RECORDING_ORDER =
            JeffreyLayout.recordingOrder(RecordingSessionFile::id, RecordingSessionFile::createdAt);

    public RecordingSession {
        if (id == null || instanceId == null || createdAt == null || files == null) {
            throw new IllegalArgumentException("A session needs its id, instance, start and files");
        }
        files = List.copyOf(files);
    }

    /** Total size in bytes of every file in this session. */
    public long totalSizeBytes() {
        return files.stream()
                .mapToLong(RecordingSessionFile::size)
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
     * <p>Newest in {@link JeffreyLayout#recordingOrder} — by the file number the profiler put in
     * the file's own name, else by the timestamp there — and emphatically not by position in a
     * listing, which is sorted for presentation, nor by the file being uncompressed, which only
     * says the compression job has not reached it yet. Not by the timestamp alone either: it goes
     * back an hour when daylight saving ends, and the chunk being written would read older than the
     * one before. Every other reader of these files orders them the same way, and a second notion
     * of "newest" in the same tree is what let the two disagree.
     *
     * <p>Load-bearing: compression refuses to touch this file and both retention rules that trim
     * files refuse to delete it.
     */
    public Optional<RecordingSessionFile> openRecording() {
        if (!newest) {
            return Optional.empty();
        }
        return recordings().max(RECORDING_ORDER);
    }

    /**
     * The session's recordings a trim, the quota or compression may touch, oldest first: every one
     * but the newest-file guard's ({@link #openRecording()}). One definition, because a reader that
     * counted chunks differently from the way it picked them would call a whole session a part of
     * itself.
     */
    public List<RecordingSessionFile> trimmableRecordings() {
        Optional<RecordingSessionFile> open = openRecording();
        return recordings()
                .filter(file -> open.filter(file::equals).isEmpty())
                .sorted(RECORDING_ORDER)
                .toList();
    }

    /**
     * When the session's newest recording started — the timestamp in its name — or the session's
     * own start while it has none. What retention measures a session's age by: a running JVM's
     * profiler loops, so its session always has a recent one.
     */
    public Instant lastRecordingStart() {
        return recordings()
                .map(RecordingSessionFile::createdAt)
                .max(Comparator.naturalOrder())
                .orElse(createdAt);
    }

    /**
     * A session that produced no data at all before a newer one replaced it — typically a
     * prematurely killed process (OOM kill, container healthcheck restart loop). The newest session
     * is never failed: it may simply not have written anything yet.
     */
    public boolean isFailedEmpty() {
        return !newest && totalSizeBytes() == 0L;
    }

    /** This session without the files of these ids, as it is once they are deleted. */
    public RecordingSession withoutFiles(Collection<String> fileIds) {
        Set<String> removed = Set.copyOf(fileIds);
        List<RecordingSessionFile> kept = files.stream()
                .filter(file -> !removed.contains(file.id()))
                .toList();
        return new RecordingSession(id, instanceId, createdAt, newest, kept);
    }

    private Stream<RecordingSessionFile> recordings() {
        return files.stream().filter(RecordingSessionFile::recording);
    }
}
