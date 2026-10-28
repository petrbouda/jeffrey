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

import java.time.Instant;

/**
 * A session directory: a visible directory in an instance, named with the time-ordered id the
 * provisioner gives a session (a UUIDv7) — the one test of "is this a session".
 *
 * @param createdAt when the session started, from its name; its order within its instance
 */
public record SessionDirectory(VolumePath path, Instant createdAt) {

    public SessionDirectory {
        if (path == null || createdAt == null) {
            throw new IllegalArgumentException("A session directory needs a path and a creation time");
        }
    }

    /** The session's id: its directory name. */
    public String id() {
        return path.name();
    }

    /** The id of the instance the session lies in: its parent directory's name. */
    public String instanceId() {
        return path.parent().name();
    }
}
