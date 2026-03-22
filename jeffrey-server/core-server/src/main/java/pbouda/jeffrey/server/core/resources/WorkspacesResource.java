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

package pbouda.jeffrey.server.core.resources;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.core.manager.project.ProjectManager;
import pbouda.jeffrey.server.core.manager.project.ProjectsManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.server.core.resources.response.ProjectResponse;
import pbouda.jeffrey.server.core.resources.response.WorkspaceResponse;
import pbouda.jeffrey.shared.common.InstantUtils;
import pbouda.jeffrey.shared.common.model.ProjectInfo;

import java.util.List;

public class WorkspacesResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspacesResource.class);

    private final WorkspacesManager workspacesManager;

    public WorkspacesResource(WorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    @GET
    public List<WorkspaceResponse> list() {
        var result = workspacesManager.findAll().stream()
                .map(wm -> {
                    var info = wm.resolveInfo();
                    return new WorkspaceResponse(
                            info.id(),
                            info.name(),
                            info.description(),
                            info.createdAt().toEpochMilli(),
                            info.projectCount(),
                            info.status());
                })
                .toList();
        LOG.debug("Listed workspaces: count={}", result.size());
        return result;
    }

    @GET
    @Path("/{workspaceId}/projects")
    public List<ProjectResponse> projects(@PathParam("workspaceId") String workspaceId) {
        WorkspaceManager workspace = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        ProjectsManager projectsManager = workspace.projectsManager();
        var result = projectsManager.findAll().stream()
                .map(ProjectManager::detailedInfo)
                .map(detail -> {
                    ProjectInfo pi = detail.projectInfo();
                    return new ProjectResponse(
                            pi.id(),
                            pi.originId(),
                            pi.name(),
                            pi.label(),
                            pi.namespace(),
                            InstantUtils.formatInstant(pi.createdAt()),
                            pi.workspaceId(),
                            detail.status(),
                            detail.profileCount(),
                            detail.recordingCount(),
                            detail.sessionCount(),
                            detail.jobCount(),
                            detail.alertCount(),
                            detail.eventSource(),
                            detail.isVirtual(),
                            detail.isOrphaned(),
                            false);
                })
                .toList();
        LOG.debug("Listed projects for workspace: workspaceId={} count={}", workspaceId, result.size());
        return result;
    }
}
