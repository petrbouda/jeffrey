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

package pbouda.jeffrey.provider.platform.repository;

/**
 * Factory interface for platform-level repositories.
 * Provides access to repositories for projects, workspaces, schedulers, and other platform entities.
 */
public interface PlatformRepositories {

    ProjectRepository newProjectRepository(String projectId);

    ProjectRecordingRepository newProjectRecordingRepository(String projectId);

    ProjectsRepository newProjectsRepository();

    SchedulerRepository newProjectSchedulerRepository(String projectId);

    SchedulerRepository newGlobalSchedulerRepository();

    ProfilerRepository newProfilerRepository();

    ProjectRepositoryRepository newProjectRepositoryRepository(String projectId);

    WorkspaceRepository newWorkspaceRepository(String workspaceId);

    WorkspacesRepository newWorkspacesRepository();

    ProfileRepository newProfileRepository(String profileId);
}
