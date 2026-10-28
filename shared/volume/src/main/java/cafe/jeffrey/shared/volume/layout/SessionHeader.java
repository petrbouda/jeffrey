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
 * A session as its instance's listing names it, nothing of it read: when it started and whether it
 * is its instance's newest.
 *
 * @param newest whether no newer session has started in its instance
 */
public record SessionHeader(SessionDirectory directory, boolean newest) {

    /** Joins a session's instance and its own directory into its name, relative to the project. */
    private static final String NAME_SEPARATOR = "/";

    public SessionHeader {
        if (directory == null) {
            throw new IllegalArgumentException("A session header needs its directory");
        }
    }

    public String id() {
        return directory.id();
    }

    public String instanceId() {
        return directory.instanceId();
    }

    /** Its name relative to the project — {@code pod-a/<id>} — as every reader of the volume lists it. */
    public String name() {
        return instanceId() + NAME_SEPARATOR + id();
    }

    public Instant createdAt() {
        return directory.createdAt();
    }

    public VolumePath path() {
        return directory.path();
    }
}
