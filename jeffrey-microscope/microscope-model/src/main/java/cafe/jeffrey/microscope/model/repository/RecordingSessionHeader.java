/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.model.repository;

import java.time.Instant;

/**
 * A recording session as its instance's listing names it, nothing of it read: when it started,
 * from its name, and whether it is its instance's newest. What it holds is read when it is opened.
 *
 * @param name   its instance and its id, {@code pod-a/<id>}
 * @param newest whether no newer session has started in its instance, so it may still grow
 */
public record RecordingSessionHeader(String id, String name, String instanceId, Instant createdAt, boolean newest) {

    public RecordingSessionHeader {
        if (id == null || name == null || instanceId == null || createdAt == null) {
            throw new IllegalArgumentException("A session header needs its id, name, instance and start");
        }
    }
}
