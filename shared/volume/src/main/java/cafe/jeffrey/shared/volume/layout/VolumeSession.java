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
 * A session as the volume says it is at the moment of reading: its header — its directory and
 * whether it is its instance's newest — and the one listing of its directory, which
 * {@link ProjectTree#files(VolumeSession)} describes its files from without listing it again.
 */
public record VolumeSession(SessionHeader header, SessionListing listing) {

    public VolumeSession {
        if (header == null || listing == null) {
            throw new IllegalArgumentException("A volume session needs its header and its listing");
        }
    }

    public String id() {
        return header.id();
    }

    public String instanceId() {
        return header.instanceId();
    }

    /** Its name relative to the project — {@code pod-a/<id>} — as every reader of the volume lists it. */
    public String name() {
        return header.name();
    }

    public Instant createdAt() {
        return header.createdAt();
    }

    public VolumePath path() {
        return header.path();
    }

    /**
     * Whether no newer session has started in its instance: only the newest session's newest
     * recording may still be on its way onto the volume, and only it can grow.
     */
    public boolean newest() {
        return header.newest();
    }
}
