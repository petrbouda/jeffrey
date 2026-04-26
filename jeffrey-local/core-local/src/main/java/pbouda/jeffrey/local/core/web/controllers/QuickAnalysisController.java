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

package pbouda.jeffrey.local.core.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pbouda.jeffrey.local.core.manager.qanalysis.QuickAnalysisManager;
import pbouda.jeffrey.local.core.resources.response.AnalyzeResponse;
import pbouda.jeffrey.local.core.resources.response.QuickGroupResponse;
import pbouda.jeffrey.local.core.resources.response.QuickRecordingResponse;
import pbouda.jeffrey.local.persistence.model.RecordingGroup;
import pbouda.jeffrey.profile.manager.ProfileManager;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.shared.common.model.Recording;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/internal/quick-analysis")
public class QuickAnalysisController {

    private static final Logger LOG = LoggerFactory.getLogger(QuickAnalysisController.class);

    private final QuickAnalysisManager quickAnalysisManager;

    public QuickAnalysisController(QuickAnalysisManager quickAnalysisManager) {
        this.quickAnalysisManager = quickAnalysisManager;
    }

    // --- Group endpoints ---

    @PostMapping(value = "/groups", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateGroupResponse createGroup(@RequestBody CreateGroupRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw Exceptions.invalidRequest("Group name is required");
        }
        String groupId = quickAnalysisManager.createGroup(request.name().trim());
        LOG.debug("Created quick analysis group: groupId={} name={}", groupId, request.name());
        return new CreateGroupResponse(groupId);
    }

    @GetMapping(value = "/groups", produces = MediaType.APPLICATION_JSON_VALUE)
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

    @DeleteMapping("/groups/{groupId}")
    public void deleteGroup(@PathVariable("groupId") String groupId) {
        quickAnalysisManager.deleteGroup(groupId);
    }

    // --- Recording endpoints ---

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public UploadRecordingResponse uploadRecording(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "groupId", required = false) String groupId) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null) {
            throw Exceptions.invalidRequest("File is required");
        }
        String normalizedGroupId = normalizeString(groupId);
        LOG.debug("Uploading recording for quick analysis: filename={} groupId={}",
                file.getOriginalFilename(), normalizedGroupId);
        try {
            String recordingId = quickAnalysisManager.uploadRecording(
                    file.getOriginalFilename(), file.getInputStream(), normalizedGroupId);
            return new UploadRecordingResponse(recordingId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded recording", e);
        }
    }

    @GetMapping(value = "/recordings", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<QuickRecordingResponse> listRecordings() {
        return quickAnalysisManager.listRecordings().stream()
                .map(this::toRecordingResponse)
                .toList();
    }

    @PutMapping(value = "/recordings/{recordingId}/group", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void moveRecordingToGroup(
            @PathVariable("recordingId") String recordingId,
            @RequestBody MoveToGroupRequest request) {
        LOG.debug("Moving quick recording to group: recordingId={} groupId={}", recordingId, request.groupId());
        quickAnalysisManager.moveRecordingToGroup(recordingId, request.groupId());
    }

    @DeleteMapping("/recordings/{recordingId}")
    public void deleteRecording(@PathVariable("recordingId") String recordingId) {
        quickAnalysisManager.deleteRecording(recordingId);
    }

    @PostMapping(value = "/recordings/{recordingId}/analyze", produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalyzeResponse analyzeRecording(@PathVariable("recordingId") String recordingId) {
        String profileId = quickAnalysisManager.analyzeRecording(recordingId);
        return new AnalyzeResponse(profileId);
    }

    @PutMapping(value = "/recordings/{recordingId}/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void updateProfile(
            @PathVariable("recordingId") String recordingId,
            @RequestBody UpdateProfileRequest request) {

        if (request == null || request.name() == null || request.name().isBlank()) {
            throw Exceptions.invalidRequest("Profile name is required");
        }

        Recording recording = quickAnalysisManager.listRecordings().stream()
                .filter(r -> r.id().equals(recordingId))
                .findFirst()
                .orElseThrow(() -> Exceptions.invalidRequest("Recording not found: " + recordingId));

        if (!recording.hasProfile()) {
            throw Exceptions.invalidRequest("Recording has no profile: " + recordingId);
        }

        quickAnalysisManager.updateProfileName(recording.profileId(), request.name().trim());
    }

    @DeleteMapping("/recordings/{recordingId}/profile")
    public void deleteProfile(@PathVariable("recordingId") String recordingId) {
        quickAnalysisManager.deleteProfile(recordingId);
    }

    private QuickRecordingResponse toRecordingResponse(Recording recording) {
        long profileSizeInBytes = 0;
        boolean profileModified = false;
        if (recording.hasProfile()) {
            ProfileManager profileManager = quickAnalysisManager.profile(recording.profileId()).orElse(null);
            if (profileManager != null) {
                profileSizeInBytes = profileManager.sizeInBytes();
                profileModified = profileManager.info().modified();
            }
        }
        return QuickRecordingResponse.from(recording, profileSizeInBytes, profileModified);
    }

    private static String normalizeString(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }

    public record CreateGroupRequest(String name) {
    }

    public record CreateGroupResponse(String groupId) {
    }

    public record UploadRecordingResponse(String recordingId) {
    }

    public record UpdateProfileRequest(String name) {
    }

    public record MoveToGroupRequest(String groupId) {
    }
}
