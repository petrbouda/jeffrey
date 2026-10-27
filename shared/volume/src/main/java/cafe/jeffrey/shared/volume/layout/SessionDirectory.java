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
import java.time.ZoneId;

/**
 * A session directory whose {@code .session-info.json} reads — likewise the one test of "is this
 * a session".
 *
 * @param createdAt when the session started — the time its name carries, else its marker's; its
 *                  order within its instance
 * @param zone      the zone the session's machine runs in, which its recordings' names are local to
 */
public record SessionDirectory(VolumePath path, Instant createdAt, ZoneId zone) {

    public SessionDirectory {
        if (path == null || createdAt == null || zone == null) {
            throw new IllegalArgumentException("A session directory needs a path, a creation time and a zone");
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
