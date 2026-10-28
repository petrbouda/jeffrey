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

package cafe.jeffrey.hub.model;

import java.time.Instant;
import java.util.Optional;

/**
 * A project as its directory on the volume names it.
 *
 * @param id          its directory name, which is also its name
 * @param workspaceId the workspace directory the project lies in
 * @param createdAt   when its directory was created, as the volume lists it; empty on a share that
 *                    lists no times
 */
public record ProjectInfo(String id, String name, String workspaceId, Optional<Instant> createdAt) {

    public ProjectInfo {
        if (id == null || name == null || workspaceId == null || createdAt == null) {
            throw new IllegalArgumentException("A project needs its id, name, workspace and creation time");
        }
    }
}
