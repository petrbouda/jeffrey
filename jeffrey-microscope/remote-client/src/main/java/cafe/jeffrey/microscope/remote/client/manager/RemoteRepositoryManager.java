/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.remote.client.manager;

import cafe.jeffrey.microscope.remote.client.FileStreamClient;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.RepositoryClient;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionResponse;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.microscope.model.repository.StreamedFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class RemoteRepositoryManager implements RepositoryManager {

    private final TempDirProvider tempDirProvider;
    private final ProjectScope scope;
    private final RepositoryStatistics statistics;
    private final RepositoryClient repositoryClient;
    private final FileStreamClient fileStreamClient;

    public RemoteRepositoryManager(
            TempDirProvider tempDirProvider,
            ProjectScope scope,
            RepositoryStatistics statistics,
            RepositoryClient repositoryClient,
            FileStreamClient fileStreamClient) {

        this.tempDirProvider = tempDirProvider;
        this.scope = scope;
        this.statistics = statistics;
        this.repositoryClient = repositoryClient;
        this.fileStreamClient = fileStreamClient;
    }

    @Override
    public List<RecordingSession> listRecordingSessions(RecordingSessionFilter filter) {
        return repositoryClient.recordingSessions(scope, filter).stream()
                .map(RecordingSessionResponse::toSession)
                .toList();
    }

    @Override
    public RecordingSession recordingSession(String sessionId) {
        return repositoryClient.recordingSession(scope, sessionId).toSession();
    }

    /**
     * The figure the project arrived with, so no request walks the project's files for it.
     */
    @Override
    public RepositoryStatistics repositoryStatistics() {
        return statistics;
    }

    /**
     * Pulls one file of a session by its id, into a temp directory of its own, under the name the
     * remote gave it — and hands back both, so the caller can release the directory when it is done
     * reading.
     *
     * <p>The session is not listed first: the transfer itself carries the name to write, so no
     * round trip to the remote is spent on an answer already on its way.
     */
    @Override
    public StreamedFile streamFile(String sessionId, String fileId) {
        TempDirectory tempDir = tempDirProvider.newTempDir();
        AtomicReference<Path> landed = new AtomicReference<>();

        try {
            fileStreamClient.streamFile(scope, sessionId, fileId, (inputStream, transferred) -> {
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
        repositoryClient.deleteSession(scope, recordingSessionId);
    }

    @Override
    public void setSessionRetained(String recordingSessionId, boolean retained) {
        repositoryClient.setSessionRetained(scope, recordingSessionId, retained);
    }
}
