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

package cafe.jeffrey.hub.client.manager;

import cafe.jeffrey.hub.client.RecordingStreamClient;
import cafe.jeffrey.hub.client.RepositoryClient;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.hub.client.dto.RepositoryFileResponse;
import cafe.jeffrey.hub.client.dto.RepositoryStatisticsResponse;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.FileCategory;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics;
import cafe.jeffrey.shared.common.model.repository.StreamedRecordingFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class RemoteRepositoryManager implements RepositoryManager {

    private final TempDirProvider tempDirProvider;
    private final ProjectInfo projectInfo;
    private final RepositoryClient repositoryClient;
    private final RecordingStreamClient recordingStreamClient;

    public RemoteRepositoryManager(
            TempDirProvider tempDirProvider,
            ProjectInfo projectInfo,
            RepositoryClient repositoryClient,
            RecordingStreamClient recordingStreamClient) {

        this.tempDirProvider = tempDirProvider;
        this.projectInfo = projectInfo;
        this.repositoryClient = repositoryClient;
        this.recordingStreamClient = recordingStreamClient;
    }

    @Override
    public List<RecordingSession> listRecordingSessions(boolean withFiles, RecordingSessionFilter filter) {
        return repositoryClient.recordingSessions(projectInfo.id(), filter).stream()
                .map(RecordingSessionResponse::from)
                .toList();
    }

    @Override
    public RecordingSession recordingSession(String sessionId) {
        return RecordingSessionResponse.from(repositoryClient.recordingSession(sessionId));
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

        return streamFile(sessionId, RepositoryFileResponse.from(fileResponse));
    }

    @Override
    public StreamedRecordingFile streamFile(String sessionId, RepositoryFile file) {
        TempDirectory tempDir = tempDirProvider.newTempDir();
        // Named by the transfer, not by the listing that chose it: the compression job may have
        // replaced the file with its archive in between, and an archive written under the
        // recording's name is read as a recording and fails.
        AtomicReference<Path> landed = new AtomicReference<>();

        try {
            RecordingStreamClient.InputStreamConsumer consumer = (inputStream, transferred) -> {
                Path tempFile = tempDir.resolve(transferred.nameOr(file.name()));
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                landed.set(tempFile);
            };

            if (file.fileType().fileCategory() == FileCategory.RECORDING) {
                recordingStreamClient.streamRecordingFile(sessionId, file.id(), consumer);
            } else {
                recordingStreamClient.streamArtifactFile(sessionId, file.id(), consumer);
            }
        } catch (Exception e) {
            tempDir.close();
            throw e;
        }

        Path tempFile = landed.get();
        return new StreamedRecordingFile(tempFile.getFileName().toString(), tempFile, tempDir::close);
    }

    @Override
    public void deleteRecordingSession(String recordingSessionId) {
        repositoryClient.deleteSession(recordingSessionId);
    }

    @Override
    public void deleteFilesInSession(String recordingSessionId, List<String> fileIds) {
        repositoryClient.deleteFilesInSession(recordingSessionId, fileIds);
    }

    @Override
    public void setSessionRetained(String recordingSessionId, boolean retained) {
        repositoryClient.setSessionRetained(recordingSessionId, retained);
    }
}
