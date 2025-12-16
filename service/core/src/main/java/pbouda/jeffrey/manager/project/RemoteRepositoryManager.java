/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.manager.project;

import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.manager.model.RepositoryStatistics;
import pbouda.jeffrey.manager.model.StreamedRecordingFile;
import pbouda.jeffrey.manager.workspace.remote.RemoteWorkspaceClient;
import pbouda.jeffrey.model.RepositoryInfo;
import pbouda.jeffrey.project.ProjectRepository;
import pbouda.jeffrey.resources.response.RecordingSessionResponse;
import pbouda.jeffrey.resources.response.RepositoryStatisticsResponse;

import java.util.List;
import java.util.Optional;

public class RemoteRepositoryManager implements RepositoryManager {

    private static final String UNSUPPORTED =
            "Not supported operation in " + RemoteRepositoryManager.class.getSimpleName();

    private final ProjectInfo projectInfo;
    private final WorkspaceInfo workspaceInfo;
    private final RemoteWorkspaceClient remoteWorkspaceClient;

    public RemoteRepositoryManager(
            ProjectInfo projectInfo,
            WorkspaceInfo workspaceInfo,
            RemoteWorkspaceClient remoteWorkspaceClient) {

        this.projectInfo = projectInfo;
        this.workspaceInfo = workspaceInfo;
        this.remoteWorkspaceClient = remoteWorkspaceClient;
    }


    @Override
    public List<RecordingSession> listRecordingSessions(boolean withFiles) {
        return remoteWorkspaceClient.recordingSessions(workspaceInfo.originId(), projectInfo.originId()).stream()
                .map(RecordingSessionResponse::from)
                .toList();
    }

    @Override
    public RepositoryStatistics calculateRepositoryStatistics() {
        RepositoryStatisticsResponse response =
                remoteWorkspaceClient.repositoryStatistics(workspaceInfo.originId(), projectInfo.originId());
        return RepositoryStatisticsResponse.from(response);
    }

    @Override
    public StreamedRecordingFile streamFile(String sessionId, String fileId) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public StreamedRecordingFile streamRecordingFiles(String sessionId, List<String> recordingFileIds) {
        throw new UnsupportedOperationException(UNSUPPORTED);
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
    public void create(ProjectRepository projectRepository) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public void createSession(WorkspaceSessionInfo workspaceSessionInfo) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public void deleteRecordingSession(String recordingSessionId, String createdBy) {
        remoteWorkspaceClient.deleteSession(workspaceInfo.originId(), projectInfo.originId(), recordingSessionId);
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
