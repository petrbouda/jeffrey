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

import pbouda.jeffrey.filesystem.ProjectDirs;
import pbouda.jeffrey.repository.ProjectRepository;
import pbouda.jeffrey.repository.model.ProjectInfo;

public class DbBasedProjectManager implements ProjectManager {

    private final ProjectInfo projectInfo;
    private final ProjectRepository projectRepository;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final ProjectDirs projectDirs;

    public DbBasedProjectManager(
            ProjectInfo projectInfo,
            ProjectDirs projectDirs,
            ProjectRepository projectRepository,
            ProfilesManager.Factory profilesManagerFactory) {

        this.projectInfo = projectInfo;
        this.projectDirs = projectDirs;
        this.projectRepository = projectRepository;
        this.profilesManagerFactory = profilesManagerFactory;
    }

    @Override
    public ProjectManager initialize() {
        projectDirs.initialize(projectInfo);
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
        return new RepositoryManagerImpl(projectDirs, projectRepository);
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
