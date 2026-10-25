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

package cafe.jeffrey.shared.volume.layout;

import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.recording.RecordingFormat;

import java.time.Instant;

/**
 * One file of a session as the volume describes it. Carries no status: which chunk the profiler
 * still holds open is a fact about the session, decided by each reader's own model.
 *
 * @param id        the file's identity within its session: its name with the recording extension
 *                  stripped, so it survives the file being compressed
 * @param name      the file's name within the session directory
 * @param createdAt when the profiler opened it — the timestamp in its own name for a chunk that
 *                  follows the naming convention, the volume's creation time otherwise
 * @param size      bytes
 * @param recording whether it is a recording chunk rather than an artifact beside one
 * @param path      where it lies on the volume
 */
public record SessionFile(String id, String name, Instant createdAt, long size, boolean recording, VolumePath path) {

    public SessionFile {
        if (id == null || name == null || createdAt == null || path == null) {
            throw new IllegalArgumentException("A session file needs an id, a name, a creation time and a path");
        }
    }

    /**
     * A file's id within its session: a recording drops its extension so the id survives
     * compression; anything else keeps its whole name. Whatever hands ids out and whatever matches
     * against them must both use this.
     */
    public static String idOf(String filename) {
        return RecordingFormat.of(filename)
                .map(format -> format.idOf(filename))
                .orElse(filename);
    }
}
