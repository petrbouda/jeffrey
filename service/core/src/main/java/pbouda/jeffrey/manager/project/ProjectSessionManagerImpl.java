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

import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;

import java.util.List;
import java.util.Optional;

public class ProjectSessionManagerImpl implements ProjectSessionManager {

    private final ProjectRepository projectRepository;

    public ProjectSessionManagerImpl(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public void createSession(WorkspaceSessionInfo workspaceSessionInfo) {
        projectRepository.createSession(workspaceSessionInfo);
    }

    @Override
    public List<WorkspaceSessionInfo> findAllSessions() {
        return projectRepository.findAllSessions();
    }

    @Override
    public Optional<WorkspaceSessionInfo> findSessionById(String sessionId) {
        return projectRepository.findSessionById(sessionId);
    }
}
