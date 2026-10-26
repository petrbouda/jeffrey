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

package cafe.jeffrey.microscope.remote.client.manager;

import cafe.jeffrey.microscope.model.ProjectInstanceInfo;
import cafe.jeffrey.microscope.model.repository.RecordingSession;

import java.util.List;

/**
 * One instance and a page of its sessions, newest first, read together in one call — what an
 * instance's own page shows. The sessions carry their files.
 *
 * @param hasOlder whether sessions older than the page's are left to read
 */
public record InstanceWithSessions(ProjectInstanceInfo instance, List<RecordingSession> sessions, boolean hasOlder) {

    public InstanceWithSessions {
        sessions = List.copyOf(sessions);
    }
}
