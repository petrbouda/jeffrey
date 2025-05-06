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

package pbouda.jeffrey.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.project.repository.RecordingSession;
import pbouda.jeffrey.project.repository.RecordingStatus;
import pbouda.jeffrey.resources.project.ProjectResource;
import pbouda.jeffrey.resources.request.CreateProjectRequest;
import pbouda.jeffrey.resources.util.Formatter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ProjectsResource {

    public record ProjectResponse(
            String id,
            String name,
            String createdAt,
            RecordingStatus status,
            int profileCount,
            int recordingCount,
            String sourceType,
            String latestRecordingAt,
            String latestProfileAt) {
    }

    public record ProfileInfo(String id, String name, String projectId, Instant createdAt) {
    }

    public record ProjectWithProfilesResponse(String id, String name, List<ProfileInfo> profiles) {
    }

    public record ProjectTemplateResponse(String id, String name) {
    }

    private final ProjectsManager projectsManager;

    public ProjectsResource(ProjectsManager projectsManager) {
        this.projectsManager = projectsManager;
    }

    @Path("/{projectId}")
    public ProjectResource projectResource(@PathParam("projectId") String projectId) {
        ProjectManager projectManager = projectsManager.project(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        return new ProjectResource(projectManager, projectsManager);
    }

    /**
     * Originally for a Dialog to pick up the Secondary Profile.
     */
    @GET
    @Path("/profiles")
    public List<ProjectWithProfilesResponse> projectsWithProfiles() {
        List<ProjectWithProfilesResponse> responses = new ArrayList<>();
        for (ProjectManager projectManager : this.projectsManager.allProjects()) {
            ProjectInfo projectInfo = projectManager.info();

            List<ProfileInfo> profiles = projectManager.profilesManager().allProfiles().stream()
                    .map(manager -> {
                        pbouda.jeffrey.common.model.ProfileInfo profileInfo = manager.info();
                        return new ProfileInfo(
                                profileInfo.id(),
                                profileInfo.name(),
                                projectInfo.id(),
                                profileInfo.createdAt());
                    })
                    .toList();

            ProjectWithProfilesResponse response =
                    new ProjectWithProfilesResponse(projectInfo.id(), projectInfo.name(), profiles);

            responses.add(response);
        }
        return responses;
    }

    @GET
    public List<ProjectResponse> projects() {
        List<ProjectResponse> responses = new ArrayList<>();
        List<? extends ProjectManager> projectManagers = this.projectsManager.allProjects();
        for (ProjectManager projectManager : projectManagers) {
            List<Recording> allRecordings = projectManager.recordingsManager().all();

            var allProfiles = projectManager.profilesManager().allProfiles();
            var latestProfile = allProfiles.stream()
                    .max(Comparator.comparing(p -> p.info().createdAt()))
                    .map(ProfileManager::info);

            String formattedLatestRecordingUploadedAt = latestRecording(allRecordings)
                    .map(rec -> Formatter.formatInstant(rec.uploadedAt()))
                    .orElse("-");

            Optional<RecordingSession> latestRecordingSession = projectManager.repositoryManager()
                    .listRecordingSessions()
                    .stream().findFirst();

            ProjectResponse response = new ProjectResponse(
                    projectManager.info().id(),
                    projectManager.info().name(),
                    Formatter.formatInstant(projectManager.info().createdAt()),
                    latestRecordingSession.map(RecordingSession::status).orElse(null),
                    allProfiles.size(),
                    allRecordings.size(),
                    latestProfile.map(profileInfo -> profileInfo.eventSource().getLabel()).orElse(null),
                    formattedLatestRecordingUploadedAt,
                    latestProfile.map(p -> Formatter.formatInstant(p.createdAt())).orElse("-")
            );

            responses.add(response);
        }

        return responses;
    }

    @GET
    @Path("/templates")
    public List<ProjectTemplateResponse> projectTemplates() {
        return projectsManager.templates().stream()
                .map(template -> new ProjectTemplateResponse(template.id(), template.name()))
                .toList();
    }

    private static Optional<Recording> latestRecording(List<Recording> allRecordings) {
        return allRecordings.stream().max(Comparator.comparing(Recording::uploadedAt));
    }

    @POST
    public Response createProfile(CreateProjectRequest request) {
        projectsManager.create(request.name(), request.templateId(), null);
        return Response.ok(allProjects()).build();
    }

    private List<ProjectInfo> allProjects() {
        return projectsManager.allProjects().stream()
                .map(ProjectManager::info)
                .sorted(Comparator.comparing(ProjectInfo::createdAt))
                .toList();
    }
}
