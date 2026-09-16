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

import cafe.jeffrey.hub.client.FileStreamClient;
import cafe.jeffrey.hub.client.RepositoryClient;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.hub.client.dto.RepositoryStatisticsResponse;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics;
import cafe.jeffrey.shared.common.model.repository.StreamedFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class RemoteRepositoryManager implements RepositoryManager {

    private final TempDirProvider tempDirProvider;
    private final ProjectInfo projectInfo;
    private final RepositoryClient repositoryClient;
    private final FileStreamClient fileStreamClient;

    public RemoteRepositoryManager(
            TempDirProvider tempDirProvider,
            ProjectInfo projectInfo,
            RepositoryClient repositoryClient,
            FileStreamClient fileStreamClient) {

        this.tempDirProvider = tempDirProvider;
        this.projectInfo = projectInfo;
        this.repositoryClient = repositoryClient;
        this.fileStreamClient = fileStreamClient;
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

    /**
     * Pulls one file of a session by its id, into a temp directory of its own, under the name the
     * hub gave it — and hands back both, so the caller can release the directory when it is done
     * reading.
     *
     * <p>This used to list the whole session first, only to read the file's name out of the entry
     * and hand it on as the name to write. The transfer carries the name now, so the listing was
     * a gRPC round trip spent on an answer already on its way.
     */
    @Override
    public StreamedFile streamFile(String sessionId, String fileId) {
        TempDirectory tempDir = tempDirProvider.newTempDir();
        AtomicReference<Path> landed = new AtomicReference<>();

        try {
            fileStreamClient.streamFile(sessionId, fileId, (inputStream, transferred) -> {
                Path target = tempDir.resolve(transferred.name());
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                landed.set(target);
            });
        } catch (Exception e) {
            tempDir.close();
            throw e;
        }

        Path target = landed.get();
        return new StreamedFile(target.getFileName().toString(), target, tempDir::close);
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
