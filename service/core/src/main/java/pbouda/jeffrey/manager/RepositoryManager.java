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

import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.manager.model.RepositoryStatistics;
import pbouda.jeffrey.model.RepositoryInfo;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.project.ProjectRepository;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface RepositoryManager {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, RepositoryManager> {
    }

    Optional<RecordingSession> findRecordingSessions(String recordingSessionId);

    List<RecordingSession> listRecordingSessions(boolean withFiles);

    /**
     * Calculates comprehensive repository statistics including session counts,
     * file type distributions, sizes, and activity timestamps.
     *
     * @return repository statistics containing all calculated metrics
     */
    RepositoryStatistics calculateRepositoryStatistics();

    void create(ProjectRepository projectRepository);

    /**
     * Create a new workspace session.
     *
     * @param workspaceSessionInfo the workspace session to create
     */
    void createSession(WorkspaceSessionInfo workspaceSessionInfo);

    Optional<RepositoryInfo> info();

    void deleteRecordingSession(String recordingSessionId);

    void deleteFilesInSession(String recordingSessionId, List<String> fileIds);

    void delete();
}
