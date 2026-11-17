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

package cafe.jeffrey.volumescheduler.volume;

/**
 * Which project: its workspace's directory and its own, as the volume names them. Two workspaces
 * may each hold a project of the same name, so a project is never named by its id alone.
 *
 * @param workspaceId the workspace directory the project lies in
 * @param projectId   its directory name: the project name the provisioner was configured with
 */
public record ProjectRef(String workspaceId, String projectId) {

    public ProjectRef {
        if (workspaceId == null || projectId == null) {
            throw new IllegalArgumentException("A project needs its workspace and its id");
        }
    }

    /** The project as structured log keys: {@code workspace_id=ws-1 project_id=orders}. */
    public String logScope() {
        return "workspace_id=" + workspaceId + " project_id=" + projectId;
    }
}
