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

package cafe.jeffrey.hub.core.streaming;

import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import io.grpc.Status;

import java.util.Comparator;
import java.util.Set;

/** The same read-only, project-scoped file selection for gRPC replay and event-activity aggregation. */
public final class ScopedReplaySource {

    private final HubPlatformRepositories repositories;
    private final RepositoryStorage.Factory storage;
    private final HubJeffreyDirs dirs;

    public ScopedReplaySource(
            HubPlatformRepositories repositories,
            RepositoryStorage.Factory storage,
            HubJeffreyDirs dirs) {
        this.repositories = repositories;
        this.storage = storage;
        this.dirs = dirs;
    }

    public ReplayStreamSubscription resolve(
            String workspaceId,
            String projectId,
            String sessionId,
            Set<String> types,
            StreamingWindow window) {

        var project = repositories.newProjectRepository(projectId)
                .find()
                .filter(info -> workspaceId.equals(info.workspaceId()))
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Project not found in requested workspace")
                        .asRuntimeException());

        var session = storage.apply(project)
                .singleSession(sessionId, true)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Session not found in requested project")
                        .asRuntimeException());

        var files = session.files().stream()
                .filter(RepositoryFile::isRecordingFile)
                .filter(RepositoryFile::isFinished)
                .sorted(Comparator.comparing(RepositoryFile::createdAt))
                .map(RepositoryFile::filePath)
                .distinct()
                .toList();

        return new ReplayStreamSubscription(sessionId, files, types, window, dirs.temp(), workspaceId, projectId);
    }
}
