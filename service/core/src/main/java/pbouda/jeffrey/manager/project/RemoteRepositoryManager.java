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

import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.manager.RepositoryManager;
import pbouda.jeffrey.manager.model.RepositoryStatistics;
import pbouda.jeffrey.manager.workspace.remote.RemoteWorkspaceClient;
import pbouda.jeffrey.model.RepositoryInfo;
import pbouda.jeffrey.project.ProjectRepository;

import java.util.List;
import java.util.Optional;

public class RemoteRepositoryManager implements RepositoryManager {

    private static final String UNSUPPORTED =
            "Not supported operation in " + RemoteRepositoryManager.class.getSimpleName();

    private final RemoteWorkspaceClient remoteWorkspaceClient;

    public RemoteRepositoryManager(RemoteWorkspaceClient remoteWorkspaceClient) {
        this.remoteWorkspaceClient = remoteWorkspaceClient;
    }

    @Override
    public Optional<RecordingSession> findRecordingSessions(String recordingSessionId) {
        return Optional.empty();
    }

    @Override
    public List<RecordingSession> listRecordingSessions(boolean withFiles) {
        return List.of();
    }

    @Override
    public RepositoryStatistics calculateRepositoryStatistics() {
        return null;
    }

    @Override
    public Optional<RepositoryInfo> info() {
        return Optional.empty();
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
    public void deleteRecordingSession(String recordingSessionId) {
        throw new UnsupportedOperationException(UNSUPPORTED);
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
