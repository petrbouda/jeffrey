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
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

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
     * <p>Always {@linkplain Selection#contiguous() contiguous}: chunk spans tile the session, so
     * every chunk between the first and last one touching the window touches it too.
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
     *
     * <p>Unlike {@link #select}, this can come back with a hole in it, because a reader ticking
     * names is free to skip one. Callers that go on to <em>merge</em> the result must refuse a
     * selection that is not {@linkplain Selection#contiguous() contiguous}.
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
     * The chunks a selection holds, each with the stretch of the recording it covers.
     *
     * <p>Kept as chunks-with-their-spans rather than as a list plus two loose bounds, because the
     * bounds alone cannot answer the one question that decides whether the selection may be
     * merged at all: whether the chunks are an unbroken run. A merge concatenates them into one
     * recording, so a selection with a hole in it produces a JFR that misstates its own span --
     * and every rate drawn from it is wrong by the size of the hole.
     */
    public record Selection(List<Chunk> chunks) {

        public Selection {
            chunks = List.copyOf(chunks);
        }

        /**
         * One chunk and the stretch it holds: from when the profiler opened it to when it opened
         * the next one.
         *
         * @param end the start of the following chunk, the session's end for the last one, or
         *            {@code null} while the session is still writing it
         */
        public record Chunk(RepositoryFile file, Instant start, Instant end) {
        }

        /**
         * Walks the session's finished chunks oldest first, handing each with its end to the
         * predicate, and keeps the ones it accepts.
         */
        private static Selection of(
                List<RepositoryFile> files, Instant finishedAt, BiPredicate<RepositoryFile, Instant> keep) {
            List<RepositoryFile> chunks = finishedChunks(files);

            List<Chunk> selected = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                RepositoryFile chunk = chunks.get(i);
                Instant chunkEnd = i + 1 < chunks.size() ? chunks.get(i + 1).createdAt() : finishedAt;
                if (keep.test(chunk, chunkEnd)) {
                    selected.add(new Chunk(chunk, chunk.createdAt(), chunkEnd));
                }
            }
            return new Selection(selected);
        }

        public boolean isEmpty() {
            return chunks.isEmpty();
        }

        /** The selected files themselves, oldest first. */
        public List<RepositoryFile> files() {
            return chunks.stream().map(Chunk::file).toList();
        }

        /** When the first selected chunk started, or {@code null} for no chunk. */
        public Instant coverageStart() {
            return chunks.isEmpty() ? null : chunks.getFirst().start();
        }

        /**
         * When the last selected chunk ended, or {@code null} for no chunk and for a chunk the
         * session is still writing.
         */
        public Instant coverageEnd() {
            return chunks.isEmpty() ? null : chunks.getLast().end();
        }

        /**
         * Whether the chunks are an unbroken run: each one ends exactly where the next begins.
         * Only such a selection may be merged, because merging is concatenation and a gap between
         * two chunks is not visible in the result. An empty selection and a single chunk are
         * trivially unbroken.
         *
         * <p>Judged over the chunks the session lists now, not over an absolute timeline. A chunk
         * deleted from the session leaves its neighbours genuinely adjacent, and nothing here can
         * know about a file that is no longer there.
         */
        public boolean contiguous() {
            for (int i = 1; i < chunks.size(); i++) {
                if (!Objects.equals(chunks.get(i - 1).end(), chunks.get(i).start())) {
                    return false;
                }
            }
            return true;
        }

        /**
         * The chunks of the session that lie between the first and last selected one but were not
         * selected — what {@link #contiguous()} refuses, named so a reader can act on it.
         */
        public List<RepositoryFile> gaps(List<RepositoryFile> files) {
            if (chunks.isEmpty()) {
                return List.of();
            }
            Set<String> selected = chunks.stream().map(chunk -> chunk.file().id()).collect(Collectors.toSet());
            return finishedChunks(files).stream()
                    .filter(chunk -> !selected.contains(chunk.id()))
                    .filter(chunk -> !chunk.createdAt().isBefore(coverageStart()))
                    .filter(chunk -> coverageEnd() == null || chunk.createdAt().isBefore(coverageEnd()))
                    .toList();
        }

        /**
         * The gap, named by the files that fill it, for a refusal a reader can act on. One
         * sentence fragment, built here rather than at each of the four layers that refuse, so
         * they cannot drift into describing the same selection differently.
         */
        public String describeGap(List<RepositoryFile> files) {
            return gaps(files).stream().map(RepositoryFile::name).collect(Collectors.joining(", "));
        }

        /**
         * Whether these are every finished chunk of the session: a recording holding them is the
         * session itself, anything less is a part of it.
         */
        public boolean isWholeSession(List<RepositoryFile> files) {
            return chunks.size() == finishedChunks(files).size();
        }

        public List<String> fileIds() {
            return chunks.stream().map(chunk -> chunk.file().id()).toList();
        }
    }

    /**
     * The session's finished recording chunks, oldest first. One definition, because a selection
     * that counted chunks differently from the way it picked them would call a whole session a
     * part of itself.
     */
    private static List<RepositoryFile> finishedChunks(List<RepositoryFile> files) {
        return files.stream()
                .filter(RepositoryFile::isRecordingFile)
                .filter(RepositoryFile::isFinished)
                .filter(file -> file.createdAt() != null)
                .sorted(Comparator.comparing(RepositoryFile::createdAt))
                .toList();
    }
}
