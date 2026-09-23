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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.microscope.model.repository.RepositoryFile;
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
        return typeOf(file.name());
    }

    public static ManagedFile typeOf(String name) {
        return ManagedFile.of(name);
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
