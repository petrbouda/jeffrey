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

import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.provider.api.repository.ProjectKeyValueRepository;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;
import pbouda.jeffrey.provider.api.repository.ProjectSchedulerRepository;

public class ProjectManagerImpl implements ProjectManager {

    private final ProjectInfo projectInfo;
    private final ProjectDirs projectDirs;
    private final ProjectRepository projectRepository;
    private final ProjectKeyValueRepository keyValueRepository;
    private final ProjectSchedulerRepository schedulerRepository;
    private final ProfilesManager.Factory profilesManagerFactory;

    public ProjectManagerImpl(
            ProjectInfo projectInfo,
            ProjectDirs projectDirs,
            ProjectRepository projectRepository,
            ProjectKeyValueRepository keyValueRepository,
            ProjectSchedulerRepository schedulerRepository,
            ProfilesManager.Factory profilesManagerFactory) {

        this.projectInfo = projectInfo;
        this.projectDirs = projectDirs;
        this.projectRepository = projectRepository;
        this.keyValueRepository = keyValueRepository;
        this.schedulerRepository = schedulerRepository;
        this.profilesManagerFactory = profilesManagerFactory;
    }

    @Override
    public void initialize() {
        projectDirs.initialize();
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
        return new RepositoryManagerImpl(projectDirs, keyValueRepository);
    }

    @Override
    public SchedulerManager schedulerManager() {
        return new SchedulerManagerImpl(schedulerRepository);
    }

    @Override
    public SettingsManager settingsManager() {
        return new SettingsManagerImpl(projectRepository);
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
    public void delete() {
        projectDirs.delete();
    }
}
