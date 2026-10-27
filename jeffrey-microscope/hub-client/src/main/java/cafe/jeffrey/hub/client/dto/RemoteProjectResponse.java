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

package cafe.jeffrey.hub.client.dto;

import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;

import java.time.Instant;

/**
 * A project as the hub reports it.
 *
 * @param createdAt   when the hub created the project, or {@code null} when the hub sent none
 * @param workspaceId the project's workspace, which is also its reference id: the hub names a
 *                    workspace by its directory on the volume
 * @param statistics  what the hub's last retention pass measured for the project
 */
public record RemoteProjectResponse(
        String id,
        String name,
        Instant createdAt,
        String workspaceId,
        RecordingStatus status,
        int sessionCount,
        RepositoryStatistics statistics) {
}
