/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.server.core.manager.project;

import cafe.jeffrey.server.core.manager.ProfilerSettingsManager;
import cafe.jeffrey.server.core.manager.RepositoryManager;
import cafe.jeffrey.server.core.manager.SchedulerManager;
import cafe.jeffrey.server.core.project.repository.RepositoryStorage;
import cafe.jeffrey.server.persistence.repository.ProjectInstanceRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.util.function.Function;

/**
 * Server-specific project manager — lean version without profile analysis,
 * recording management, or download capabilities. The server is a pure collector.
 */
public interface ProjectManager {

    record DetailedProjectInfo(
            ProjectInfo projectInfo,
            RecordingStatus status,
            int sessionCount,
            boolean isDeleted) {
    }

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, ProjectManager> {
    }

    RepositoryStorage repositoryStorage();

    RepositoryManager repositoryManager();

    SchedulerManager schedulerManager();

    ProfilerSettingsManager profilerSettingsManager();

    ProjectInstanceRepository projectInstanceRepository();

    ProjectInfo info();

    DetailedProjectInfo detailedInfo();

    void restore();

    void delete(WorkspaceEventCreator createdBy);
}
