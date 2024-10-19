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

import pbouda.jeffrey.exception.InvalidUserInput;
import pbouda.jeffrey.filesystem.FilesystemUtils;
import pbouda.jeffrey.filesystem.ProjectDirs;
import pbouda.jeffrey.model.RepositoryInfo;
import pbouda.jeffrey.repository.ProjectRepository;
import pbouda.jeffrey.repository.ProjectRepository.Key;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class RepositoryManagerImpl implements RepositoryManager {

    private final ProjectDirs projectDirs;
    private final ProjectRepository projectRepository;

    public RepositoryManagerImpl(ProjectDirs projectDirs, ProjectRepository projectRepository) {
        this.projectDirs = projectDirs;
        this.projectRepository = projectRepository;
    }


    @Override
    public void createOrReplace(Path repositoryPath, boolean createIfNotExists) {
        if (!Files.exists(repositoryPath) && createIfNotExists) {
            try {
                FilesystemUtils.createDirectories(repositoryPath);
            } catch (Exception e) {
                throw new InvalidUserInput("Cannot create a new directory for the repository directory", e);
            }
        }
        projectRepository.insert(Key.REPOSITORY_PATH, repositoryPath.toString());
    }

    @Override
    public RepositoryInfo info() {
        Optional<Path> repositoryOpt = projectRepository.getString(Key.REPOSITORY_PATH)
                .map(Path::of);

        if (repositoryOpt.isEmpty()) {
            return RepositoryInfo.notActive();
        }
        Path repository = repositoryOpt.get();

        // Repository path exists and it's a directory
        boolean repositoryPathExists = Files.isDirectory(repository);
        return RepositoryInfo.active(repositoryPathExists, repository.toString());
    }

    @Override
    public void delete() {
        projectRepository.delete(Key.REPOSITORY_PATH);
    }

    @Override
    public void generate() {
        Path repositoryPath = projectRepository.getString(Key.REPOSITORY_PATH)
                .map(Path::of)
                .orElseThrow(() -> new InvalidUserInput("Repository path is not set"));


    }
}
