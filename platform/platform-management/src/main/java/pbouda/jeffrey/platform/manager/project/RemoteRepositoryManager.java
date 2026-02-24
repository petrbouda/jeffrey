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

package pbouda.jeffrey.platform.manager.project;

import pbouda.jeffrey.platform.manager.RepositoryManager;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteRecordingStreamClient;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteRepositoryClient;
import pbouda.jeffrey.platform.resources.response.RecordingSessionResponse;
import pbouda.jeffrey.platform.resources.response.RepositoryFileResponse;
import pbouda.jeffrey.platform.resources.response.RepositoryStatisticsResponse;
import pbouda.jeffrey.profile.manager.model.RepositoryStatistics;
import pbouda.jeffrey.profile.manager.model.StreamedRecordingFile;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs.Directory;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.repository.RecordingSession;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

public class RemoteRepositoryManager implements RepositoryManager {

    private static final String UNSUPPORTED =
            "Not supported operation in " + RemoteRepositoryManager.class.getSimpleName();

    private final JeffreyDirs jeffreyDirs;
    private final ProjectInfo projectInfo;
    private final WorkspaceInfo workspaceInfo;
    private final RemoteRepositoryClient repositoryClient;
    private final RemoteRecordingStreamClient recordingStreamClient;

    public RemoteRepositoryManager(
            JeffreyDirs jeffreyDirs,
            ProjectInfo projectInfo,
            WorkspaceInfo workspaceInfo,
            RemoteRepositoryClient repositoryClient,
            RemoteRecordingStreamClient recordingStreamClient) {

        this.jeffreyDirs = jeffreyDirs;
        this.projectInfo = projectInfo;
        this.workspaceInfo = workspaceInfo;
        this.repositoryClient = repositoryClient;
        this.recordingStreamClient = recordingStreamClient;
    }


    @Override
    public List<RecordingSession> listRecordingSessions(boolean withFiles) {
        return repositoryClient.recordingSessions(workspaceInfo.originId(), projectInfo.originId()).stream()
                .map(RecordingSessionResponse::from)
                .toList();
    }

    @Override
    public RepositoryStatistics calculateRepositoryStatistics() {
        RepositoryStatisticsResponse response =
                repositoryClient.repositoryStatistics(workspaceInfo.originId(), projectInfo.originId());
        return RepositoryStatisticsResponse.from(response);
    }

    @Override
    public StreamedRecordingFile streamArtifact(String sessionId, String artifactId) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public StreamedRecordingFile mergeAndStreamRecordings(String sessionId, List<String> recordingFileIds) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public StreamedRecordingFile streamFile(String sessionId, String fileId) {
        RecordingSessionResponse session = repositoryClient.recordingSession(
                workspaceInfo.originId(), projectInfo.originId(), sessionId);

        RepositoryFileResponse fileResponse = session.files().stream()
                .filter(f -> f.id().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        Directory tempDir = jeffreyDirs.newTempDir();
        Path tempFile = tempDir.resolve(fileResponse.name());

        try {
            recordingStreamClient.streamSingleFile(
                    workspaceInfo.originId(), projectInfo.originId(), sessionId, fileId,
                    (inputStream, _) -> {
                        Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    });
        } catch (Exception e) {
            tempDir.close();
            throw e;
        }

        return new StreamedRecordingFile(fileResponse.name(), tempFile, tempDir::close);
    }

    @Override
    public Optional<RecordingSession> findRecordingSessions(String recordingSessionId) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public Optional<RepositoryInfo> info() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public void create(RepositoryInfo repositoryInfo) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public void createSession(ProjectInstanceSessionInfo projectInstanceSessionInfo) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public void deleteRecordingSession(String recordingSessionId, WorkspaceEventCreator createdBy) {
        repositoryClient.deleteSession(workspaceInfo.originId(), projectInfo.originId(), recordingSessionId);
    }

    @Override
    public void deleteFilesInSession(String recordingSessionId, List<String> fileIds) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public void delete() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }
}
