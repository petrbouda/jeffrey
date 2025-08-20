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

import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.common.model.repository.RepositoryFile;
import pbouda.jeffrey.manager.model.RepositoryStatistics;
import pbouda.jeffrey.model.RepositoryInfo;
import pbouda.jeffrey.project.ProjectRepository;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;

import java.util.List;
import java.util.Optional;

public class RepositoryManagerImpl implements RepositoryManager {

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
    public RepositoryStatistics calculateRepositoryStatistics() {
        List<RecordingSession> sessions = listRecordingSessions();

        if (sessions.isEmpty()) {
            return new RepositoryStatistics(
                    0, RecordingStatus.UNKNOWN, 0L, 0L, 0, 0L, 0, 0, 0);
        }

        // Sessions are already sorted by date (newest first) from listSessions()
        RecordingSession latestSession = sessions.getFirst();

        // Calculate aggregated statistics in a single pass
        long totalSize = 0;
        int totalFiles = 0;
        int jfrFiles = 0;
        int heapDumpFiles = 0;
        int otherFiles = 0;
        long biggestSessionSize = 0;

        for (RecordingSession session : sessions) {
            long sessionSize = 0;

            for (RepositoryFile file : session.files()) {
                totalFiles++;
                long fileSize = file.size() != null ? file.size() : 0L;
                totalSize += fileSize;
                sessionSize += fileSize;

                // Count by file type
                switch (file.fileType()) {
                    case JFR -> jfrFiles++;
                    case HEAP_DUMP -> heapDumpFiles++;
                    default -> otherFiles++;
                }
            }

            biggestSessionSize = Math.max(biggestSessionSize, sessionSize);
        }

        long lastActivityTime = latestSession.createdAt() != null
                ? latestSession.createdAt().toEpochMilli()
                : 0L;

        return new RepositoryStatistics(
                sessions.size(),
                latestSession.status(),
                lastActivityTime,
                totalSize,
                totalFiles,
                biggestSessionSize,
                jfrFiles,
                heapDumpFiles,
                otherFiles
        );
    }

    @Override
    public void create(ProjectRepository projectRepository) {
        DBRepositoryInfo dbRepositoryInfo = new DBRepositoryInfo(
                projectRepository.type(), projectRepository.finishedSessionDetectionFile());

        repository.insert(dbRepositoryInfo);
    }

    @Override
    public Optional<RepositoryInfo> info() {
        return repository.getAll().stream()
                .findFirst()
                .map(repository -> {
                    return new RepositoryInfo(
                            repository.type(),
                            repository.finishedSessionDetectionFile());
                });
    }

    @Override
    public void deleteRecordingSession(String recordingSessionId) {
        recordingRepository.deleteSession(recordingSessionId);
    }

    @Override
    public void deleteFilesInSession(String recordingSessionId, List<String> fileIds) {
        recordingRepository.deleteRepositoryFiles(recordingSessionId, fileIds);
    }

    @Override
    public void delete() {
        repository.deleteAll();
    }
}
