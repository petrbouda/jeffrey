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
import pbouda.jeffrey.common.model.RepositoryType;
import pbouda.jeffrey.exception.InvalidUserInputException;
import pbouda.jeffrey.model.RepositoryInfo;
import pbouda.jeffrey.project.AsyncProfilerRepositoryOperations;
import pbouda.jeffrey.project.JdkRepositoryOperations;
import pbouda.jeffrey.project.RepositoryOperations;
import pbouda.jeffrey.project.repository.RecordingSession;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.provider.api.RecordingInitializer;
import pbouda.jeffrey.provider.api.RecordingOperations;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;

import java.nio.file.Files;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

public class RepositoryManagerImpl implements RepositoryManager {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryManagerImpl.class);

    private final ProjectRepositoryRepository repository;
    private final RemoteRepositoryStorage recordingRepository;
    private final RecordingOperations repositoryOperations;
    private final RecordingInitializer recordingInitializer;

    private static final EnumMap<RepositoryType, RepositoryOperations> REPOSITORY_OPERATIONS =
            new EnumMap<>(RepositoryType.class);

    static {
        REPOSITORY_OPERATIONS.put(RepositoryType.ASYNC_PROFILER, new AsyncProfilerRepositoryOperations());
        REPOSITORY_OPERATIONS.put(RepositoryType.JDK, new JdkRepositoryOperations());
    }

    public RepositoryManagerImpl(
            ProjectRepositoryRepository repository,
            RemoteRepositoryStorage recordingRepository,
            RecordingOperations repositoryOperations,
            RecordingInitializer recordingInitializer) {

        this.repository = repository;
        this.recordingRepository = recordingRepository;
        this.repositoryOperations = repositoryOperations;
        this.recordingInitializer = recordingInitializer;
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
    public void downloadRecordingSession(String recordingSessionId, boolean merge) {
        recordingRepository.listRecordings(recordingSessionId);

//        try (NewRecordingHolder holder = recordingInitializer.newRecording(new NewRecording(filename, folderId))) {
//            holder.transferFrom(stream);
//        } catch (Exception e) {
//            throw new RuntimeException("Cannot upload the recording: " + filename, e);
//        }
//
//        LOG.info("Uploaded recording: name={} folder_id={} project_id={}",
//                filename, folderId, projectInfo.id());
    }

    @Override
    public void downloadRecording(String recordingId) {

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

    @Override
    public void generate() {
//        Path repositoryPath = repository.getString(ProjectKeyValueRepository.Key.REPOSITORY_PATH)
//                .map(Path::of)
//                .orElseThrow(() -> new InvalidUserInputException("Repository path is not set"));
//        FileSystemUtils.concatFiles();
    }
}
