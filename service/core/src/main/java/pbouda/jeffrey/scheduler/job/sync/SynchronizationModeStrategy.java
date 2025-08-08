/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.scheduler.job.sync;

import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.scheduler.job.model.SynchronizationMode;
import pbouda.jeffrey.scheduler.model.WorkspaceProject;

import java.nio.file.Path;
import java.util.List;

public interface SynchronizationModeStrategy {

    /**
     * Executes the synchronization mode strategy with project belonging to the same workspace.
     *
     * @param projects List of projects to synchronize.
     * @param projectManagers List of projectManagers to synchronize with.
     * @param templateId The ID of the project template to use for synchronization.
     */
    void executeOnWorkspace(
            List<WorkspaceProject> projects,
            List<? extends ProjectManager> projectManagers,
            String templateId);

    /**
     * Returns the synchronization mode associated with this strategy.
     *
     * @return The synchronization mode.
     */
    SynchronizationMode synchronizationMode();
}
