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
 * A session as the volume says it is at the moment of reading: its directory, whether it is its
 * instance's newest, and the one listing of its directory — {@link ProjectTree#files(VolumeSession)}
 * describes its files from that listing, without listing the directory again.
 *
 * @param newest whether no newer session has started in its instance: only the newest session's
 *               newest recording may still be on its way onto the volume, and only it can grow
 */
public record VolumeSession(SessionDirectory directory, boolean newest, SessionListing listing) {

    /** Joins a session's instance and its own directory into its name, relative to the project. */
    private static final String NAME_SEPARATOR = "/";

    public VolumeSession {
        if (directory == null || listing == null) {
            throw new IllegalArgumentException("A volume session needs its directory and its listing");
        }
    }

    public String id() {
        return directory.id();
    }

    public String instanceId() {
        return directory.instanceId();
    }

    /** Its name relative to the project — {@code pod-a/s-1} — as every reader of the volume lists it. */
    public String name() {
        return instanceId() + NAME_SEPARATOR + id();
    }

    public Instant createdAt() {
        return directory.createdAt();
    }

    public ZoneId zone() {
        return directory.zone();
    }

    public VolumePath path() {
        return directory.path();
    }
}
