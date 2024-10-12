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

import pbouda.jeffrey.filesystem.FilesystemUtils;
import pbouda.jeffrey.filesystem.ProjectDirs;
import pbouda.jeffrey.common.Recording;
import pbouda.jeffrey.repository.ProjectsRepository;
import pbouda.jeffrey.repository.model.ProjectInfo;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public class DbBasedProjectManager implements ProjectManager {

    private final ProjectInfo projectInfo;
    private final ProjectsRepository repository;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final ProjectDirs projectDirs;

    public DbBasedProjectManager(
            ProjectInfo projectInfo,
            ProjectDirs projectDirs,
            ProjectsRepository repository,
            ProfilesManager.Factory profilesManagerFactory) {

        this.projectInfo = projectInfo;
        this.projectDirs = projectDirs;
        this.repository = repository;
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
    public List<Recording> recordings() {
        return projectDirs.allRecordings();
    }

    @Override
    public Path uploadRecording(String filename, InputStream stream) {
        return projectDirs.uploadRecording(filename, stream);
    }

    @Override
    public void deleteRecording(Path file) {
        Path recording = projectDirs.recordingsDir().resolve(file);
        FilesystemUtils.delete(recording);
    }

    @Override
    public ProjectInfo info() {
        return projectInfo;
    }

    @Override
    public void cleanup() {
        projectDirs.delete();
    }
}
