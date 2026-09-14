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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The chunks of a session's recording, as every consumer of "the session's JFR" reads them off
 * a file listing: the hub when it compresses, replays or trims them, and Microscope when it
 * assembles them into the recording a profile is built from.
 *
 * <p>Two rules live here and nowhere else. <strong>One file per id.</strong> A chunk keeps its id
 * across compression — the id is the name with the chunk extension stripped — and for a moment
 * the raw {@code .jfr} and its {@code .jfr.lz4} lie side by side, both finished; a listing taken
 * then holds the same chunk twice. The compressed form is the one taken: the hub writes it
 * whole and moves it into place, so it is complete whenever it exists, where the raw one is
 * about to be deleted. <strong>Oldest first.</strong> A recording is its chunks in the order they
 * were written; a chunk whose time is unknown goes last rather than failing the sort.
 */
public final class RecordingChunks {

    private static final Comparator<RepositoryFile> OLDEST_FIRST = Comparator.comparing(
            RepositoryFile::createdAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder()));

    private RecordingChunks() {
    }

    /**
     * The finished chunks among the files, one per id, oldest first.
     */
    public static List<RepositoryFile> finished(List<RepositoryFile> files) {
        Map<String, RepositoryFile> byId = new LinkedHashMap<>();
        for (RepositoryFile file : files) {
            if (file.isRecordingChunk() && file.isFinished()) {
                byId.merge(file.id(), file, RecordingChunks::preferred);
            }
        }
        return byId.values().stream()
                .sorted(OLDEST_FIRST)
                .toList();
    }

    /**
     * Of two listings of the same file, the one that is complete: the form the hub compressed is
     * written whole and moved into place, so it wins over the raw original it replaces. Two of
     * the same form are the same file, and the first listed is kept.
     */
    public static RepositoryFile preferred(RepositoryFile first, RepositoryFile second) {
        boolean secondIsCompressed = second.fileType().isCompressedByHub();
        boolean firstIsCompressed = first.fileType().isCompressedByHub();
        return secondIsCompressed && !firstIsCompressed ? second : first;
    }
}
