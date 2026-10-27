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

package cafe.jeffrey.hub.core.project.repository;

/**
 * What deleting a session did. An instance goes with its last session, since an instance with no
 * session left has nothing to show; a caller counting what it reclaimed counts both.
 */
public enum SessionDeletion {
    NOT_FOUND,
    SESSION_DELETED,
    SESSION_AND_INSTANCE_DELETED;

    public boolean deleted() {
        return this != NOT_FOUND;
    }

    public boolean instanceDeleted() {
        return this == SESSION_AND_INSTANCE_DELETED;
    }
}
