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

package pbouda.jeffrey.local.core.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.manager.qanalysis.QuickAnalysisManager;
import pbouda.jeffrey.local.core.resources.response.AnalyzeResponse;
import pbouda.jeffrey.local.core.resources.response.QuickGroupResponse;
import pbouda.jeffrey.local.core.resources.response.QuickRecordingResponse;
import pbouda.jeffrey.local.persistence.model.RecordingGroup;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.resources.ProfileResource;
import pbouda.jeffrey.profile.resources.ProfileResourceFactory;
import pbouda.jeffrey.shared.common.model.Recording;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class QuickAnalysisResource {

    private static final Logger LOG = LoggerFactory.getLogger(QuickAnalysisResource.class);

    private final QuickAnalysisManager quickAnalysisManager;
    private final ProfileResourceFactory profileResourceFactory;

    public QuickAnalysisResource(
            QuickAnalysisManager quickAnalysisManager,
            ProfileResourceFactory profileResourceFactory) {
        this.quickAnalysisManager = quickAnalysisManager;
        this.profileResourceFactory = profileResourceFactory;
    }

    // --- Group endpoints ---

    @POST
    @Path("/groups")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createGroup(CreateGroupRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("Group name is required");
        }

        String groupId = quickAnalysisManager.createGroup(request.name().trim());
        LOG.debug("Created quick analysis group: groupId={} name={}", groupId, request.name());
        return Response.ok(new CreateGroupResponse(groupId)).build();
    }

    @GET
    @Path("/groups")
    @Produces(MediaType.APPLICATION_JSON)
    public List<QuickGroupResponse> listGroups() {
        List<RecordingGroup> groups = quickAnalysisManager.listGroups();
        List<Recording> recordings = quickAnalysisManager.listRecordings();

        Map<String, Long> countByGroup = recordings.stream()
                .filter(r -> r.groupId() != null)
                .collect(Collectors.groupingBy(Recording::groupId, Collectors.counting()));

        return groups.stream()
                .map(g -> QuickGroupResponse.from(g, countByGroup.getOrDefault(g.id(), 0L).intValue()))
                .toList();
    }

    @DELETE
    @Path("/groups/{groupId}")
    public Response deleteGroup(@PathParam("groupId") String groupId) {
        quickAnalysisManager.deleteGroup(groupId);
        return Response.ok().build();
    }

    // --- Recording endpoints ---

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadRecording(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition cdh,
            @FormDataParam("groupId") String groupId) {

        if (fileInputStream == null || cdh == null || cdh.getFileName() == null) {
            throw new BadRequestException("File is required");
        }

        String normalizedGroupId = normalizeString(groupId);
        LOG.debug("Uploading recording for quick analysis: filename={} groupId={}", cdh.getFileName(), normalizedGroupId);

        String recordingId = quickAnalysisManager.uploadRecording(cdh.getFileName(), fileInputStream, normalizedGroupId);
        return Response.ok(new UploadRecordingResponse(recordingId)).build();
    }

    @GET
    @Path("/recordings")
    @Produces(MediaType.APPLICATION_JSON)
    public List<QuickRecordingResponse> listRecordings() {
        return quickAnalysisManager.listRecordings().stream()
                .map(this::toRecordingResponse)
                .toList();
    }

    private QuickRecordingResponse toRecordingResponse(Recording recording) {
        long profileSizeInBytes = 0;
        if (recording.hasProfile()) {
            ProfileManager profileManager = quickAnalysisManager.profile(recording.profileId()).orElse(null);
            if (profileManager != null) {
                profileSizeInBytes = profileManager.sizeInBytes();
            }
        }
        return QuickRecordingResponse.from(recording, profileSizeInBytes);
    }

    @PUT
    @Path("/recordings/{recordingId}/group")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response moveRecordingToGroup(
            @PathParam("recordingId") String recordingId,
            MoveToGroupRequest request) {
        LOG.debug("Moving quick recording to group: recordingId={} groupId={}", recordingId, request.groupId());
        quickAnalysisManager.moveRecordingToGroup(recordingId, request.groupId());
        return Response.ok().build();
    }

    @DELETE
    @Path("/recordings/{recordingId}")
    public Response deleteRecording(@PathParam("recordingId") String recordingId) {
        quickAnalysisManager.deleteRecording(recordingId);
        return Response.ok().build();
    }

    @POST
    @Path("/recordings/{recordingId}/analyze")
    @Produces(MediaType.APPLICATION_JSON)
    public Response analyzeRecording(@PathParam("recordingId") String recordingId) {
        String profileId = quickAnalysisManager.analyzeRecording(recordingId);
        return Response.ok(new AnalyzeResponse(profileId)).build();
    }

    @PUT
    @Path("/recordings/{recordingId}/profile")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateProfile(
            @PathParam("recordingId") String recordingId,
            UpdateProfileRequest request) {

        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new BadRequestException("Profile name is required");
        }

        Recording recording = quickAnalysisManager.listRecordings().stream()
                .filter(r -> r.id().equals(recordingId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Recording not found: " + recordingId));

        if (!recording.hasProfile()) {
            throw new BadRequestException("Recording has no profile: " + recordingId);
        }

        quickAnalysisManager.updateProfileName(recording.profileId(), request.name().trim());
        return Response.ok().build();
    }

    @DELETE
    @Path("/recordings/{recordingId}/profile")
    public Response deleteProfile(@PathParam("recordingId") String recordingId) {
        quickAnalysisManager.deleteProfile(recordingId);
        return Response.ok().build();
    }

    // --- Profile sub-resource (kept for profile access) ---

    @Path("/profiles/{profileId}")
    public ProfileResource profileResource(@PathParam("profileId") String profileId) {
        ProfileManager profileManager = quickAnalysisManager.profile(profileId)
                .orElseThrow(() -> new NotFoundException("Quick analysis profile not found: " + profileId));
        return profileResourceFactory.create(profileManager);
    }

    // --- DTOs ---

    public record CreateGroupRequest(String name) {}

    public record CreateGroupResponse(String groupId) {}

    public record UploadRecordingResponse(String recordingId) {}

    public record UpdateProfileRequest(String name) {}

    public record MoveToGroupRequest(String groupId) {}

    // --- Helpers ---

    private static String normalizeString(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }
}
