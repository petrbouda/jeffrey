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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.storage.recording.api.file.FileCategory;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;

/**
 * What Microscope makes of a file a hub listed.
 *
 * <p>The hub says which files a session holds and which of them are recordings; it does not say
 * what the rest are, because it never reads them. Microscope does, so it classifies the name
 * itself with its own {@link ManagedFile} — here, once, rather than at every reader of a listing.
 */
public final class RepositoryFiles {

    private RepositoryFiles() {
    }

    public static ManagedFile typeOf(RepositoryFile file) {
        return ManagedFile.of(file.name());
    }

    /**
     * A file worth fetching on its own — a log, a dump, a perf-counters file — as opposed to a
     * recording chunk, which a download takes, or the profiler's scratch file, which nothing
     * takes. Judged on the hub's word for what is a recording and Microscope's for the rest.
     */
    public static boolean isArtifact(RepositoryFile file) {
        return !file.isRecordingFile() && typeOf(file).fileCategory() == FileCategory.ARTIFACT;
    }
}
