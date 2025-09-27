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

package pbouda.jeffrey.resources.workspace;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.resources.response.ProjectResponse;
import pbouda.jeffrey.resources.response.WorkspaceEventResponse;
import pbouda.jeffrey.resources.response.WorkspaceResponse;
import pbouda.jeffrey.resources.util.InstantUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class WorkspaceResource {

    private final WorkspaceManager workspaceManager;

    public WorkspaceResource(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @DELETE
    public void delete() {
        workspaceManager.delete();
    }

    @GET
    public WorkspaceResponse info() {
        return WorkspaceMappers.toResponse(workspaceManager.resolveInfo());
    }

    @GET
    @Path("/events")
    public List<WorkspaceEventResponse> events() {
        return workspaceManager.workspaceEventManager().findEvents().stream()
                .map(WorkspaceMappers::toEventResponse)
                .toList();
    }

    @GET
    @Path("/projects")
    public List<ProjectResponse> projects() {
        List<ProjectResponse> responses = new ArrayList<>();

        List<? extends ProjectManager> projectManagers = workspaceManager.findAllProjects();
        for (ProjectManager projectManager : projectManagers) {
            List<Recording> allRecordings = projectManager.recordingsManager().all();

            var allProfiles = projectManager.profilesManager().allProfiles();
            var latestProfile = allProfiles.stream()
                    .max(Comparator.comparing(p -> p.info().createdAt()))
                    .map(ProfileManager::info);

            List<RecordingSession> recordingSessions = projectManager.repositoryManager()
                    .listRecordingSessions(false);

            RecordingStatus recordingStatus = recordingSessions.stream()
                    .limit(1)
                    .findAny()
                    .map(RecordingSession::status).orElse(null);

            ProjectInfo projectInfo = projectManager.info();
            ProjectResponse response = new ProjectResponse(
                    projectInfo.id(),
                    projectInfo.name(),
                    InstantUtils.formatInstant(projectInfo.createdAt()),
                    recordingStatus,
                    allProfiles.size(),
                    allRecordings.size(),
                    recordingSessions.size(),
                    latestProfile.map(profileInfo -> profileInfo.eventSource().getLabel()).orElse(null));

            responses.add(response);
        }

        return responses;
    }
}
