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

package cafe.jeffrey.shared.volume;

import java.time.Instant;

/**
 * One thing on the volume and what a single look at it tells: its kind, its size and when it was
 * created. A backend answers all of it from one call per entry (a stat, a listing row), since on a
 * network mount or an object store every further question is another round trip.
 *
 * @param path      where it lies
 * @param kind      a file, a directory, or anything else (a socket, a device) that nobody reads
 * @param size      bytes; meaningful for a file only
 * @param createdAt when the backend says it was created; wrong for a file rewritten in place
 */
public record VolumeEntry(VolumePath path, Kind kind, long size, Instant createdAt) {

    public enum Kind {
        FILE,
        DIRECTORY,
        OTHER
    }

    public VolumeEntry {
        if (path == null || kind == null || createdAt == null) {
            throw new IllegalArgumentException("A volume entry needs a path, a kind and a creation time");
        }
    }

    public String name() {
        return path.name();
    }

    public boolean isFile() {
        return kind == Kind.FILE;
    }

    public boolean isDirectory() {
        return kind == Kind.DIRECTORY;
    }
}
