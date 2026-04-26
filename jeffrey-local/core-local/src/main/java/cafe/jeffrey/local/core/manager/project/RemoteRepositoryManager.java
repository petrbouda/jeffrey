/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.local.core.manager.project;

import cafe.jeffrey.local.core.LocalJeffreyDirs;
import cafe.jeffrey.local.core.manager.RepositoryManager;
import cafe.jeffrey.local.core.client.RemoteRecordingStreamClient;
import cafe.jeffrey.local.core.client.RemoteRepositoryClient;
import cafe.jeffrey.local.core.resources.response.RecordingSessionResponse;
import cafe.jeffrey.local.core.resources.response.RepositoryFileResponse;
import cafe.jeffrey.local.core.resources.response.RepositoryStatisticsResponse;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.FileCategory;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics;
import cafe.jeffrey.shared.common.model.repository.StreamedRecordingFile;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class RemoteRepositoryManager implements RepositoryManager {

    private final LocalJeffreyDirs jeffreyDirs;
    private final ProjectInfo projectInfo;
    private final RemoteRepositoryClient repositoryClient;
    private final RemoteRecordingStreamClient recordingStreamClient;

    public RemoteRepositoryManager(
            LocalJeffreyDirs jeffreyDirs,
            ProjectInfo projectInfo,
            RemoteRepositoryClient repositoryClient,
            RemoteRecordingStreamClient recordingStreamClient) {

        this.jeffreyDirs = jeffreyDirs;
        this.projectInfo = projectInfo;
        this.repositoryClient = repositoryClient;
        this.recordingStreamClient = recordingStreamClient;
    }

    @Override
    public List<RecordingSession> listRecordingSessions(boolean withFiles) {
        return repositoryClient.recordingSessions(projectInfo.id()).stream()
                .map(RecordingSessionResponse::from)
                .toList();
    }

    @Override
    public RepositoryStatistics calculateRepositoryStatistics() {
        RepositoryStatisticsResponse response =
                repositoryClient.repositoryStatistics(projectInfo.id());
        return RepositoryStatisticsResponse.from(response);
    }

    @Override
    public StreamedRecordingFile streamFile(String sessionId, String fileId) {
        RecordingSessionResponse session = repositoryClient.recordingSession(sessionId);

        RepositoryFileResponse fileResponse = session.files().stream()
                .filter(f -> f.id().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        TempDirectory tempDir = jeffreyDirs.newTempDir();
        Path tempFile = tempDir.resolve(fileResponse.name());

        try {
            RemoteRecordingStreamClient.InputStreamConsumer consumer = (inputStream, _) -> {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            };

            if (fileResponse.fileType().fileCategory() == FileCategory.RECORDING) {
                recordingStreamClient.streamRecordingFile(sessionId, fileId, consumer);
            } else {
                recordingStreamClient.streamArtifactFile(sessionId, fileId, consumer);
            }
        } catch (Exception e) {
            tempDir.close();
            throw e;
        }

        return new StreamedRecordingFile(fileResponse.name(), tempFile, tempDir::close);
    }

    @Override
    public void deleteRecordingSession(String recordingSessionId, WorkspaceEventCreator createdBy) {
        repositoryClient.deleteSession(recordingSessionId);
    }

    @Override
    public void deleteFilesInSession(String recordingSessionId, List<String> fileIds) {
        repositoryClient.deleteFilesInSession(recordingSessionId, fileIds);
    }
}
