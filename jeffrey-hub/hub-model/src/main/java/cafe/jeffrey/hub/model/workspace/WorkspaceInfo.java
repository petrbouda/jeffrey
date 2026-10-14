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

package cafe.jeffrey.hub.model.workspace;

import java.time.Instant;

/**
 * A workspace row: the reference id names its directory under the hub's workspaces root,
 * and the status says whether that directory is there.
 */
public record WorkspaceInfo(
        String id,
        String referenceId,
        String name,
        Instant createdAt,
        WorkspaceStatus status,
        int projectCount) {

    public WorkspaceInfo withId(String newId) {
        return new WorkspaceInfo(newId, referenceId, name, createdAt, status, projectCount);
    }

    public WorkspaceInfo withStatus(WorkspaceStatus newStatus) {
        return new WorkspaceInfo(id, referenceId, name, createdAt, newStatus, projectCount);
    }
}
