/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.FlywayMigration;
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.repository.project.ProjectRepositories;
import pbouda.jeffrey.common.model.ProjectInfo;

public class ProjectManagerImpl implements ProjectManager {

    private final ProjectInfo projectInfo;
    private final ProjectRepositories projectRepositories;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final ProjectDirs projectDirs;

    public ProjectManagerImpl(
            ProjectInfo projectInfo,
            ProjectDirs projectDirs,
            ProjectRepositories projectRepositories,
            ProfilesManager.Factory profilesManagerFactory) {

        this.projectInfo = projectInfo;
        this.projectDirs = projectDirs;
        this.projectRepositories = projectRepositories;
        this.profilesManagerFactory = profilesManagerFactory;
    }

    @Override
    public ProjectManager initialize() {
        projectDirs.initialize(projectInfo);
        // Initialize Project's tables
        FlywayMigration.migrate(projectDirs);
        return this;
    }

    @Override
    public ProfilesManager profilesManager() {
        return profilesManagerFactory.apply(projectInfo.id());
    }

    @Override
    public RecordingsManager recordingsManager() {
        return new FileBasedRecordingsManager(projectDirs);
    }

    @Override
    public RepositoryManager repositoryManager() {
        return new RepositoryManagerImpl(projectDirs, projectRepositories.keyValue());
    }

    @Override
    public SchedulerManager schedulerManager() {
        return new SchedulerManagerImpl(projectRepositories.scheduler());
    }

    @Override
    public SettingsManager settingsManager() {
        return new SettingsManagerImpl(projectInfo, projectDirs);
    }

    @Override
    public ProjectInfo info() {
        return projectInfo;
    }

    @Override
    public ProjectDirs dirs() {
        return projectDirs;
    }

    @Override
    public void cleanup() {
        projectDirs.delete();
    }
}
