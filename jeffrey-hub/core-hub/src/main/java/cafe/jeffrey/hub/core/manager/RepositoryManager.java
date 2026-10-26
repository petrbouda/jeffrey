/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.hub.core.manager;

import cafe.jeffrey.shared.volume.layout.SessionDeletion;

/**
 * What the retention pass changes in a project's repository through the hub, so the change is
 * logged and announced as a JFR event.
 */
public interface RepositoryManager {

    /**
     * Deletes a session, and its instance's directory when no other directory is left in it.
     * {@link SessionDeletion#NOT_FOUND} names a session already gone, which a caller counting
     * reclaimed bytes must not count.
     */
    SessionDeletion deleteRecordingSession(String recordingSessionId);
}
