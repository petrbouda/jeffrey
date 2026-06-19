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

package cafe.jeffrey.shared.ui.workspace.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.ui.workspace.bridge.RecordingProfileInfoProvider;
import cafe.jeffrey.shared.ui.workspace.dto.RecordingGroupResponse;
import cafe.jeffrey.shared.ui.workspace.dto.RecordingResponse;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared local recordings store: groups + uploads + listing + per-file download + deletion. Backed
 * by the deployment-agnostic {@link RecordingsCoreManager}; the per-recording profile fields are
 * supplied by the {@link RecordingProfileInfoProvider} bridge (a real implementation in microscope,
 * {@link RecordingProfileInfoProvider#NOOP} elsewhere). Profile-mutating endpoints (analyze / update
 * / delete profile) are NOT here — they remain in the deployment that owns profiles.
 *
 * <p>Annotated {@code @RestController} and registered as a {@code @Bean} via
 * {@code WorkspacesFeatureConfiguration}; it lives outside both apps' component-scan roots, so it is
 * mapped exactly once (by the {@code @Bean}).
 */
@RestController
@RequestMapping("/api/internal/recordings")
public class RecordingsController {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingsController.class);

    private final RecordingsCoreManager recordingsManager;
    private final RecordingProfileInfoProvider profileInfoProvider;

    public RecordingsController(
            RecordingsCoreManager recordingsManager,
            RecordingProfileInfoProvider profileInfoProvider) {
        this.recordingsManager = recordingsManager;
        this.profileInfoProvider = profileInfoProvider;
    }

    // --- Group endpoints ---

    @PostMapping(value = "/groups", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateGroupResponse createGroup(@RequestBody CreateGroupRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw Exceptions.invalidRequest("Group name is required");
        }
        String groupId = recordingsManager.createGroup(request.name().trim());
        LOG.debug("Created quick analysis group: groupId={} name={}", groupId, request.name());
        return new CreateGroupResponse(groupId);
    }

    @GetMapping(value = "/groups", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RecordingGroupResponse> listGroups() {
        List<RecordingGroup> groups = recordingsManager.listGroups();
        List<Recording> recordings = recordingsManager.listRecordings();

        Map<String, Long> countByGroup = recordings.stream()
                .filter(r -> r.groupId() != null)
                .collect(Collectors.groupingBy(Recording::groupId, Collectors.counting()));

        return groups.stream()
                .map(g -> RecordingGroupResponse.from(g, countByGroup.getOrDefault(g.id(), 0L).intValue()))
                .toList();
    }

    @DeleteMapping("/groups/{groupId}")
    public void deleteGroup(@PathVariable("groupId") String groupId) {
        recordingsManager.deleteGroup(groupId);
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
            String recordingId = recordingsManager.uploadRecording(
                    file.getOriginalFilename(), file.getInputStream(), normalizedGroupId);
            return new UploadRecordingResponse(recordingId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded recording", e);
        }
    }

    @PostMapping(value = "/from-path", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public UploadRecordingResponse importFromPath(@RequestBody ImportFromPathRequest request) {
        if (request == null || request.path() == null || request.path().isBlank()) {
            throw Exceptions.invalidRequest("Path is required");
        }
        String path = request.path().trim();
        LOG.debug("Importing recording from local path for quick analysis: path={}", path);
        String recordingId = recordingsManager.importRecordingFromPath(Path.of(path));
        return new UploadRecordingResponse(recordingId);
    }

    @GetMapping(value = "/recordings", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RecordingResponse> listRecordings() {
        List<Recording> recordings = recordingsManager.listRecordings();
        Map<String, List<RecordingTag>> tagsByRecording = recordingsManager.tagsForRecordings(
                recordings.stream().map(Recording::id).toList());

        return recordings.stream()
                .map(rec -> RecordingResponse.from(
                        rec,
                        profileInfoProvider.profileInfo(rec),
                        tagsByRecording.getOrDefault(rec.id(), List.of())))
                .toList();
    }

    @PutMapping(value = "/recordings/{recordingId}/group", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void moveRecordingToGroup(
            @PathVariable("recordingId") String recordingId,
            @RequestBody MoveToGroupRequest request) {
        LOG.debug("Moving quick recording to group: recordingId={} groupId={}", recordingId, request.groupId());
        recordingsManager.moveRecordingToGroup(recordingId, request.groupId());
    }

    @DeleteMapping("/recordings/{recordingId}")
    public void deleteRecording(@PathVariable("recordingId") String recordingId) {
        recordingsManager.deleteRecording(recordingId);
    }

    @GetMapping("/recordings/{recordingId}/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable("recordingId") String recordingId,
            @PathVariable("fileId") String fileId) {

        Path filePath = recordingsManager.findRecordingFile(recordingId, fileId)
                .orElseThrow(() -> Exceptions.invalidRequest(
                        "Recording file not found: recordingId=" + recordingId + " fileId=" + fileId));

        FileSystemResource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filePath.getFileName().toString() + "\"")
                .body(resource);
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

    public record ImportFromPathRequest(String path) {
    }

    public record MoveToGroupRequest(String groupId) {
    }
}
