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
import cafe.jeffrey.shared.volume.liveness.SessionLifecycle;

import java.time.Instant;
import java.time.ZoneId;

/**
 * A session as the volume says it is at the moment of reading: its directory, whether it is still
 * recording, and whether it is pinned against retention. Without its files — a listing reads those
 * only for the sessions it keeps.
 */
public record VolumeSession(SessionDirectory directory, SessionLifecycle lifecycle, boolean retained) {

    public VolumeSession {
        if (directory == null || lifecycle == null) {
            throw new IllegalArgumentException("A volume session needs its directory and its lifecycle");
        }
    }

    public String id() {
        return directory.id();
    }

    public String instanceId() {
        return directory.instanceId();
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
