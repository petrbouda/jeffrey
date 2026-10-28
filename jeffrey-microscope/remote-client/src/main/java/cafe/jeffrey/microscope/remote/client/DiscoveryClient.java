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

package cafe.jeffrey.microscope.remote.client;

import cafe.jeffrey.microscope.remote.client.dto.RemoteProjectResponse;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;

import java.util.List;
import java.util.Optional;

/**
 * The workspaces and projects a source holds: the top two levels of its tree, read off the
 * recording volume's directories.
 */
public interface DiscoveryClient {

    record WorkspaceResult(WorkspaceInfo info, WorkspaceStatus status) {
        public static WorkspaceResult of(WorkspaceStatus status) {
            return new WorkspaceResult(null, status);
        }

        public static WorkspaceResult of(WorkspaceInfo info) {
            return new WorkspaceResult(info, WorkspaceStatus.AVAILABLE);
        }
    }

    record PublicApiInfo(String version) {
    }

    /** What the source says about itself; throws when it cannot be reached. */
    PublicApiInfo info();

    List<WorkspaceInfo> allWorkspaces();

    /** One workspace, its status saying so when it is missing or the source cannot be reached. */
    WorkspaceResult workspace(String workspaceId);

    /**
     * One workspace without collapsing remote failures into an offline state, for a caller that
     * reports whether the source rejected, timed out, or could not be reached.
     */
    Optional<WorkspaceInfo> workspaceOrThrow(String workspaceId);

    void deleteWorkspace(String workspaceId);

    List<RemoteProjectResponse> allProjects(String workspaceId);

    Optional<RemoteProjectResponse> project(String workspaceId, String projectId);
}
