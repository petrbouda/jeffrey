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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.exception.InvalidUserInputException;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.model.RepositoryInfo;
import pbouda.jeffrey.common.model.RepositoryType;
import pbouda.jeffrey.project.AsyncProfilerRepositoryOperations;
import pbouda.jeffrey.project.JdkRepositoryOperations;
import pbouda.jeffrey.project.RepositoryOperations;
import pbouda.jeffrey.provider.api.repository.ProjectKeyValueRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Optional;

public class RepositoryManagerImpl implements RepositoryManager {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryManagerImpl.class);

    private final ProjectDirs projectDirs;
    private final ProjectKeyValueRepository repository;

    private static final EnumMap<RepositoryType, RepositoryOperations> REPOSITORY_OPERATIONS =
            new EnumMap<>(RepositoryType.class);

    static {
        REPOSITORY_OPERATIONS.put(RepositoryType.ASYNC_PROFILER, new AsyncProfilerRepositoryOperations());
        REPOSITORY_OPERATIONS.put(RepositoryType.JDK, new JdkRepositoryOperations());
    }

    public RepositoryManagerImpl(ProjectDirs projectDirs, ProjectKeyValueRepository repository) {
        this.projectDirs = projectDirs;
        this.repository = repository;
    }

    @Override
    public void createOrReplace(Path repositoryPath, RepositoryType repositoryType, boolean createIfNotExists) {
        if (!Files.exists(repositoryPath) && createIfNotExists) {
            try {
                FileSystemUtils.createDirectories(repositoryPath);
            } catch (Exception e) {
                LOG.error("Cannot create a new directory for the repository: {}", e.getMessage());
                throw new InvalidUserInputException("Cannot create a new directory for the repository", e);
            }
        }

        ObjectNode repositoryObject = Json.createObject()
                .put("path", repositoryPath.toString())
                .put("type", repositoryType.name());

        repository.insert(ProjectKeyValueRepository.Key.REPOSITORY_PATH, repositoryObject);
    }

    @Override
    public Optional<RepositoryInfo> info() {
        return repository.getJson(ProjectKeyValueRepository.Key.REPOSITORY_PATH)
                .map(repository -> {
                    Path repositoryPath = Path.of(repository.get("path").asText());
                    RepositoryType repositoryType = RepositoryType.valueOf(repository.get("type").asText());
                    boolean repositoryPathExists = Files.isDirectory(repositoryPath);
                    return new RepositoryInfo(repositoryPathExists, repositoryPath, repositoryType);
                });
    }

    @Override
    public void delete() {
        repository.delete(ProjectKeyValueRepository.Key.REPOSITORY_PATH);
    }

    @Override
    public void generate() {
        Path repositoryPath = repository.getString(ProjectKeyValueRepository.Key.REPOSITORY_PATH)
                .map(Path::of)
                .orElseThrow(() -> new InvalidUserInputException("Repository path is not set"));

//        FileSystemUtils.concatFiles();
    }
}
