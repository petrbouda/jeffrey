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

package cafe.jeffrey.shared.common.model.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * A time window over a session's recording, and which of its chunks cover it.
 *
 * <p>A hub session can run for days and roll a chunk every few minutes, and the question is almost
 * never about all of it. A chunk's {@link RepositoryFile#createdAt()} is the moment the profiler
 * opened it — the timestamp in the file's own name — so chunk <em>n</em> covers everything from its
 * own start to the start of chunk <em>n+1</em>, and the last one runs to the session's end, or is
 * still open while the session records. A chunk is selected when that span touches the window.
 *
 * <p>Both bounds are optional: a window with no start reaches back to the first chunk, one with no
 * end reaches forward to the last. A window with neither is not a window, and the whole-session
 * download exists for that.
 *
 * @param start the first instant of interest, or {@code null} for the session's start
 * @param end   the last instant of interest, or {@code null} for the session's end
 */
public record ChunkWindow(Instant start, Instant end) {

    public ChunkWindow {
        if (start == null && end == null) {
            throw new IllegalArgumentException("A window needs a start or an end");
        }
        if (start != null && end != null && !start.isBefore(end)) {
            throw new IllegalArgumentException("The window's start must be before its end");
        }
    }

    public static ChunkWindow ofEpochMillis(Long startMillis, Long endMillis) {
        return new ChunkWindow(
                startMillis == null ? null : Instant.ofEpochMilli(startMillis),
                endMillis == null ? null : Instant.ofEpochMilli(endMillis));
    }

    /**
     * The finished chunks of the session that cover this window, oldest first, with the span
     * they actually cover — which is what a profile built from them is about, and is wider than
     * the window whenever a chunk straddles a bound.
     *
     * @param files      the session's files; anything that is not a finished chunk is ignored
     * @param finishedAt when the session stopped recording, or {@code null} while it still does
     */
    public Selection select(List<RepositoryFile> files, Instant finishedAt) {
        return Selection.of(files, finishedAt, (chunk, chunkEnd) -> covers(chunk.createdAt(), chunkEnd));
    }

    /**
     * The chunks named by id, with the span they cover — the same answer as a window gives, for a
     * reader who chose the files by name from a listing instead of by time.
     */
    public static Selection ofFiles(List<RepositoryFile> files, Set<String> fileIds, Instant finishedAt) {
        return Selection.of(files, finishedAt, (chunk, chunkEnd) -> fileIds.contains(chunk.id()));
    }

    /**
     * Whether a chunk spanning {@code [chunkStart, chunkEnd)} touches the window; a chunk with no
     * end is still being appended to by the session and reaches as far forward as the window does.
     */
    private boolean covers(Instant chunkStart, Instant chunkEnd) {
        boolean startsBeforeWindowEnds = end == null || chunkStart.isBefore(end);
        boolean endsAfterWindowStarts = start == null || chunkEnd == null || chunkEnd.isAfter(start);
        return startsBeforeWindowEnds && endsAfterWindowStarts;
    }

    /**
     * @param chunks        the covering chunks, oldest first; empty when the window lies outside
     *                      the recording
     * @param coverageStart when the first selected chunk started, or {@code null} for no chunk
     * @param coverageEnd   when the last selected chunk ended, or {@code null} for no chunk and
     *                      for a chunk the session is still writing
     */
    public record Selection(List<RepositoryFile> chunks, Instant coverageStart, Instant coverageEnd) {

        /**
         * Walks the session's finished chunks oldest first, handing each with its end to the
         * predicate, and keeps the ones it accepts together with the span they cover.
         */
        private static Selection of(
                List<RepositoryFile> files, Instant finishedAt, BiPredicate<RepositoryFile, Instant> keep) {
            List<RepositoryFile> chunks = files.stream()
                    .filter(RepositoryFile::isRecordingFile)
                    .filter(RepositoryFile::isFinished)
                    .filter(file -> file.createdAt() != null)
                    .sorted(Comparator.comparing(RepositoryFile::createdAt))
                    .toList();

            List<RepositoryFile> selected = new ArrayList<>();
            Instant coverageStart = null;
            Instant coverageEnd = null;
            for (int i = 0; i < chunks.size(); i++) {
                RepositoryFile chunk = chunks.get(i);
                Instant chunkEnd = i + 1 < chunks.size() ? chunks.get(i + 1).createdAt() : finishedAt;
                if (!keep.test(chunk, chunkEnd)) {
                    continue;
                }
                selected.add(chunk);
                if (coverageStart == null) {
                    coverageStart = chunk.createdAt();
                }
                coverageEnd = chunkEnd;
            }
            return new Selection(List.copyOf(selected), coverageStart, coverageEnd);
        }

        public boolean isEmpty() {
            return chunks.isEmpty();
        }

        /**
         * Whether these are every finished chunk of the session: a recording holding them is the
         * session itself, anything less is a part of it.
         */
        public boolean isWholeSession(List<RepositoryFile> files) {
            long finishedChunks = files.stream()
                    .filter(RepositoryFile::isRecordingFile)
                    .filter(RepositoryFile::isFinished)
                    .count();
            return chunks.size() == finishedChunks;
        }

        public List<String> fileIds() {
            return chunks.stream().map(RepositoryFile::id).toList();
        }
    }
}
