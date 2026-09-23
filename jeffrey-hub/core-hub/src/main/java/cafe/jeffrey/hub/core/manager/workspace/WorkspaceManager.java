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

package cafe.jeffrey.hub.core.manager.workspace;

import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;

import java.util.function.Function;

public interface WorkspaceManager {

    @FunctionalInterface
    interface Factory extends Function<WorkspaceInfo, WorkspaceManager> {
    }

    /**
     * Returns the locally stored workspace information without remote resolution.
     * Unlike {@link #resolveInfo()}, this never makes network calls.
     *
     * @return the locally stored workspace information
     */
    WorkspaceInfo localInfo();

    /**
     * Returns the workspace information associated with this manager.
     * For remote workspaces, this may involve network calls to check availability.
     *
     * @return the workspace information
     */
    WorkspaceInfo resolveInfo();

    /**
     * Returns the projects manager for managing multiple projects within the workspace.
     *
     * @return the projects manager
     */
    ProjectsManager projectsManager();

    /**
     * Deletes the workspace and all its data.
     * The workspace can be recreated by the synchronizer from filesystem events.
     */
    void delete();
}
