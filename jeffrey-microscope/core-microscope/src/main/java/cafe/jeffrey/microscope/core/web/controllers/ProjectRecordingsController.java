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

package cafe.jeffrey.microscope.core.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import cafe.jeffrey.microscope.core.manager.ProfilesManager;
import cafe.jeffrey.microscope.core.manager.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.resources.request.CreateGroupRequest;
import cafe.jeffrey.microscope.core.resources.request.MoveRecordingRequest;
import cafe.jeffrey.microscope.core.resources.response.RecordingFileResponse;
import cafe.jeffrey.microscope.core.resources.response.RecordingsResponse;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/internal/remote-servers/{serverId}/workspaces/{workspaceId}/projects/{projectId}/recordings")
public class ProjectRecordingsController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRecordingsController.class);

    private final ProjectManagerResolver resolver;

    public ProjectRecordingsController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public List<RecordingsResponse> recordings(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        ProjectManager projectManager = resolver.resolve(serverId, workspaceId, projectId).projectManager();
        RecordingsManager recordingsManager = projectManager.recordingsManager();
        ProfilesManager profilesManager = projectManager.profilesManager();

        Map<String, ProfileManager> profileByRecordingId = profilesManager.allProfiles().stream()
                .filter(pm -> pm.info().recordingId() != null)
                .collect(Collectors.toMap(
                        pm -> pm.info().recordingId(),
                        pm -> pm,
                        (existing, _) -> existing));

        var result = recordingsManager.all().stream()
                .map(rec -> {
                    List<RecordingFileResponse> recordingFiles = rec.files().stream()
                            .map(ProjectRecordingsController::toRecordingFile)
                            .toList();
                    long sizeInBytesTotal = recordingFiles.stream()
                            .mapToLong(RecordingFileResponse::sizeInBytes)
                            .sum();

                    ProfileManager pm = profileByRecordingId.get(rec.id());
                    ProfileInfo profileInfo = pm != null ? pm.info() : null;

                    return new RecordingsResponse(
                            rec.id(),
                            rec.recordingName(),
                            sizeInBytesTotal,
                            rec.recordingDuration().toMillis(),
                            rec.createdAt().toEpochMilli(),
                            rec.groupId(),
                            rec.eventSource().name(),
                            profileInfo != null,
                            profileInfo != null ? profileInfo.id() : null,
                            profileInfo != null ? profileInfo.name() : null,
                            profileInfo != null && profileInfo.enabled(),
                            profileInfo != null && profileInfo.modified(),
                            pm != null ? pm.sizeInBytes() : 0,
                            recordingFiles);
                })
                .toList();
        LOG.debug("Listed recordings: projectId={} count={}", projectId, result.size());
        return result;
    }

    @PostMapping("/groups")
    public ResponseEntity<Void> createGroup(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @RequestBody CreateGroupRequest request) {
        LOG.debug("Creating recording group: groupName={}", request.groupName());
        recordings(serverId, workspaceId, projectId, mgr -> mgr.createGroup(request.groupName()));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/groups")
    public List<RecordingGroup> findAllGroups(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        RecordingsManager mgr = resolver.resolve(serverId, workspaceId, projectId).projectManager().recordingsManager();
        var result = mgr.allRecordingGroups();
        LOG.debug("Listed recording groups: projectId={} count={}", projectId, result.size());
        return result;
    }

    @DeleteMapping("/groups/{groupId}")
    public void deleteGroup(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("groupId") String groupId) {
        LOG.debug("Deleting recording group: groupId={}", groupId);
        recordings(serverId, workspaceId, projectId, mgr -> mgr.deleteGroup(groupId));
    }

    @PutMapping("/{recordingId}/group")
    public void moveRecordingToGroup(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("recordingId") String recordingId,
            @RequestBody MoveRecordingRequest request) {
        LOG.debug("Moving recording to group: recordingId={} groupId={}", recordingId, request.groupId());
        recordings(serverId, workspaceId, projectId, mgr -> mgr.moveRecordingToGroup(recordingId, request.groupId()));
    }

    @DeleteMapping("/{recordingId}")
    public void deleteRecording(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("recordingId") String recordingId) {
        LOG.debug("Deleting recording: recordingId={}", recordingId);
        recordings(serverId, workspaceId, projectId, mgr -> mgr.delete(recordingId));
    }

    @GetMapping(value = "/{recordingId}/files/{fileId}/download",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("recordingId") String recordingId,
            @PathVariable("fileId") String fileId) {

        LOG.debug("Downloading recording file: recordingId={} fileId={}", recordingId, fileId);
        RecordingsManager mgr = resolver.resolve(serverId, workspaceId, projectId).projectManager().recordingsManager();
        Path filePath = mgr.findRecordingFile(recordingId, fileId)
                .orElseThrow(() -> Exceptions.invalidRequest(
                        "Recording file not found: recordingId=" + recordingId + ", fileId=" + fileId));

        StreamingResponseBody body = output -> {
            try (InputStream input = Files.newInputStream(filePath)) {
                input.transferTo(output);
            }
        };

        String filename = recordingId + "-" + filePath.getFileName().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(FileSystemUtils.size(filePath)))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    private static RecordingFileResponse toRecordingFile(RecordingFile recordingFile) {
        return new RecordingFileResponse(
                recordingFile.id(),
                recordingFile.filename(),
                recordingFile.sizeInBytes(),
                recordingFile.recordingFileType().name(),
                recordingFile.recordingFileType().description());
    }

    private void recordings(String serverId, String workspaceId, String projectId, java.util.function.Consumer<RecordingsManager> action) {
        action.accept(resolver.resolve(serverId, workspaceId, projectId).projectManager().recordingsManager());
    }
}
