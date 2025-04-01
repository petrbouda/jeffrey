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

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.provider.api.model.recording.RecordingWithFolder;
import pbouda.jeffrey.resources.project.ProjectResource;
import pbouda.jeffrey.resources.request.CreateProjectRequest;
import pbouda.jeffrey.resources.util.Formatter;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Path("/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectsResource {

    public record ProjectResponse(
            String id,
            String name,
            String createdAt,
            int profileCount,
            int recordingCount,
            int alertCount,
            String sourceType,
            String latestRecordingAt,
            String latestProfileAt) {
    }

    public record ProfileInfo(String id, String name, String projectId, Instant createdAt) {
    }

    public record ProjectWithProfilesResponse(String id, String name, List<ProfileInfo> profiles) {
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProjectsManager projectsManager;

    @Inject
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
        for (ProjectManager projectManager : this.projectsManager.allProjects()) {
            List<RecordingWithFolder> allRecordings = projectManager.recordingsManager().all();

            var allProfiles = projectManager.profilesManager().allProfiles();
            var latestProfile = allProfiles.stream()
                    .max(Comparator.comparing(p -> p.info().createdAt()))
                    .map(ProfileManager::info);

            ProjectResponse response = new ProjectResponse(
                    projectManager.info().id(),
                    projectManager.info().name(),
                    projectManager.info().createdAt().toString(),
                    allProfiles.size(),
                    allRecordings.size(),
                    3,
                    latestProfile.map(profileInfo -> profileInfo.eventSource().getLabel()).orElse(null),
                    latestRecording(allRecordings).map(rec -> FORMATTER.format(rec.recording().uploadedAt())).orElse("-"),
                    latestProfile.map(p -> Formatter.formatInstant(p.createdAt())).orElse("-")
            );

            responses.add(response);
        }

        return responses;
    }

    private static Optional<RecordingWithFolder> latestRecording(List<RecordingWithFolder> allRecordings) {
        return allRecordings.stream().max(Comparator.comparing(rec -> rec.recording().uploadedAt()));
    }

    @POST
    public Response createProfile(CreateProjectRequest request) {
        projectsManager.create(new ProjectInfo(request.name()));
        return Response.ok(allProjects()).build();
    }

    private List<ProjectInfo> allProjects() {
        return projectsManager.allProjects().stream()
                .map(ProjectManager::info)
                .sorted(Comparator.comparing(ProjectInfo::createdAt))
                .toList();
    }
}
