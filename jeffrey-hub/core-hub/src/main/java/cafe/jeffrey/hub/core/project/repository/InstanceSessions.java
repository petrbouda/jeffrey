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

import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;

import java.util.List;

/**
 * One instance with its sessions, newest first, read together so the instance's status and the
 * sessions it is read off can never disagree.
 */
public record InstanceSessions(ProjectInstanceInfo instance, List<RecordingSession> sessions) {

    public InstanceSessions {
        sessions = List.copyOf(sessions);
    }
}
