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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.exception.InvalidUserInputException;
import pbouda.jeffrey.model.RepositoryInfo;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

public class RepositoryManagerImpl implements RepositoryManager {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryManagerImpl.class);

    private final ProjectRepositoryRepository repository;
    private final RemoteRepositoryStorage recordingRepository;

    public RepositoryManagerImpl(
            ProjectRepositoryRepository repository,
            RemoteRepositoryStorage recordingRepository) {

        this.repository = repository;
        this.recordingRepository = recordingRepository;
    }

    @Override
    public Optional<RecordingSession> findRecordingSessions(String recordingSessionId) {
        return recordingRepository.listSessions().stream()
                .filter(session -> session.id().equals(recordingSessionId))
                .findFirst();
    }

    @Override
    public List<RecordingSession> listRecordingSessions() {
        return recordingRepository.listSessions();
    }

    @Override
    public void createOrReplace(boolean createIfNotExists, RepositoryInfo repositoryInfo) {
        if (!Files.exists(repositoryInfo.repositoryPath()) && createIfNotExists) {
            try {
                FileSystemUtils.createDirectories(repositoryInfo.repositoryPath());
            } catch (Exception e) {
                LOG.error("Cannot create a new directory for the repository: {}", e.getMessage());
                throw new InvalidUserInputException("Cannot create a new directory for the repository", e);
            }
        }

        // Currently, we can configure only one repository.
        List<DBRepositoryInfo> repositories = repository.getAll();
        if (!repositories.isEmpty()) {
            repository.deleteAll();
        }

        DBRepositoryInfo dbRepositoryInfo = new DBRepositoryInfo(
                repositoryInfo.repositoryPath(),
                repositoryInfo.repositoryType(),
                repositoryInfo.finishedSessionDetectionFile());

        repository.insert(dbRepositoryInfo);
    }

    @Override
    public Optional<RepositoryInfo> info() {
        return repository.getAll().stream()
                .findFirst()
                .map(repository -> {
                    return new RepositoryInfo(
                            repository.path(),
                            repository.type(),
                            repository.finishedSessionDetectionFile());
                });
    }

    @Override
    public void delete() {
        repository.deleteAll();
    }
}
