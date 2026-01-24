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

package pbouda.jeffrey.platform.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import pbouda.jeffrey.platform.manager.qanalysis.QuickAnalysisManager;
import pbouda.jeffrey.platform.resources.util.InstantUtils;
import pbouda.jeffrey.profile.ai.service.HeapDumpContextExtractor;
import pbouda.jeffrey.profile.ai.service.OqlAssistantService;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.profile.resources.ProfileResource;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * REST resource for quick/ad-hoc JFR analysis.
 * Allows uploading JFR files and analyzing them without creating workspaces or projects.
 */
public class QuickAnalysisResource {

    /**
     * Response DTO for profile information.
     */
    public record ProfileResponse(
            String id,
            String name,
            String createdAt,
            RecordingEventSource eventSource,
            boolean enabled,
            long durationInMillis,
            long sizeInBytes) {
    }

    /**
     * Response DTO for analysis result.
     */
    public record AnalyzeResponse(String profileId) {
    }

    private final QuickAnalysisManager quickAnalysisManager;
    private final OqlAssistantService oqlAssistantService;
    private final HeapDumpContextExtractor heapDumpContextExtractor;

    public QuickAnalysisResource(
            QuickAnalysisManager quickAnalysisManager,
            OqlAssistantService oqlAssistantService,
            HeapDumpContextExtractor heapDumpContextExtractor) {
        this.quickAnalysisManager = quickAnalysisManager;
        this.oqlAssistantService = oqlAssistantService;
        this.heapDumpContextExtractor = heapDumpContextExtractor;
    }

    /**
     * Upload and analyze a JFR file.
     * The file is saved to the quick-recordings directory and then parsed.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response upload(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition cdh) {

        if (fileInputStream == null || cdh == null || cdh.getFileName() == null) {
            throw new BadRequestException("File is required");
        }

        try {
            String profileId = quickAnalysisManager.uploadAndAnalyze(cdh.getFileName(), fileInputStream).get();
            return Response.ok(new AnalyzeResponse(profileId)).build();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new InternalServerErrorException("Failed to analyze JFR file: " + e.getMessage(), e);
        }
    }

    /**
     * List all quick analysis profiles.
     */
    @GET
    @Path("/profiles")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProfileResponse> listProfiles() {
        return quickAnalysisManager.listProfiles().stream()
                .sorted(Comparator.comparing(ProfileInfo::createdAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    /**
     * Access a specific quick analysis profile.
     */
    @Path("/profiles/{profileId}")
    public ProfileResource profileResource(@PathParam("profileId") String profileId) {
        ProfileManager profileManager = quickAnalysisManager.profile(profileId)
                .orElseThrow(() -> new NotFoundException("Quick analysis profile not found: " + profileId));
        return new ProfileResource(profileManager, oqlAssistantService, heapDumpContextExtractor);
    }

    /**
     * Delete a quick analysis profile.
     */
    @DELETE
    @Path("/profiles/{profileId}")
    public Response deleteProfile(@PathParam("profileId") String profileId) {
        quickAnalysisManager.deleteProfile(profileId);
        return Response.noContent().build();
    }

    private ProfileResponse toResponse(ProfileInfo profileInfo) {
        ProfileManager profileManager = quickAnalysisManager.profile(profileInfo.id()).orElse(null);
        long sizeInBytes = profileManager != null ? profileManager.sizeInBytes() : 0;

        return new ProfileResponse(
                profileInfo.id(),
                profileInfo.name(),
                InstantUtils.formatInstant(profileInfo.createdAt()),
                profileInfo.eventSource(),
                profileInfo.enabled(),
                profileInfo.duration().toMillis(),
                sizeInBytes);
    }
}
