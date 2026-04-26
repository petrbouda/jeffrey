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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import pbouda.jeffrey.local.core.manager.RecordingsDownloadManager;
import pbouda.jeffrey.local.core.manager.RepositoryManager;
import pbouda.jeffrey.local.core.manager.project.ProjectManager;
import pbouda.jeffrey.local.core.resources.request.SelectedRecordingsRequest;
import pbouda.jeffrey.local.core.resources.response.RecordingSessionResponse;
import pbouda.jeffrey.local.core.resources.response.RepositoryStatisticsResponse;
import pbouda.jeffrey.local.core.web.ProjectManagerResolver;
import pbouda.jeffrey.shared.common.model.repository.RepositoryStatistics;
import pbouda.jeffrey.shared.common.model.repository.StreamedRecordingFile;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/api/internal/workspaces/{workspaceId}/projects/{projectId}/repository")
public class ProjectRepositoryController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRepositoryController.class);

    private final ProjectManagerResolver resolver;
    private final Clock clock;

    public ProjectRepositoryController(ProjectManagerResolver resolver, Clock clock) {
        this.resolver = resolver;
        this.clock = clock;
    }

    @GetMapping("/sessions")
    public List<RecordingSessionResponse> listRepositorySessions(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        ProjectManager pm = resolver.resolve(workspaceId, projectId).projectManager();
        var result = pm.repositoryManager().listRecordingSessions(true).stream()
                .map(s -> RecordingSessionResponse.from(s, clock))
                .toList();
        LOG.debug("Listed repository sessions: projectId={} count={}", projectId, result.size());
        return result;
    }

    @GetMapping("/statistics")
    public RepositoryStatisticsResponse getRepositoryStatistics(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        LOG.debug("Fetching repository statistics");
        ProjectManager pm = resolver.resolve(workspaceId, projectId).projectManager();
        RepositoryStatistics stats = pm.repositoryManager().calculateRepositoryStatistics();
        return RepositoryStatisticsResponse.from(stats);
    }

    @PostMapping("/sessions/download")
    public void downloadSession(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @RequestBody SelectedRecordingsRequest request) {
        LOG.debug("Downloading session recordings: sessionId={}", request.sessionId());
        RecordingsDownloadManager mgr = resolver.resolve(workspaceId, projectId).projectManager().recordingsDownloadManager();
        mgr.mergeAndDownloadSession(request.sessionId());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public void deleteSession(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("sessionId") String sessionId) {
        LOG.debug("Deleting repository session: sessionId={}", sessionId);
        RepositoryManager mgr = resolver.resolve(workspaceId, projectId).projectManager().repositoryManager();
        mgr.deleteRecordingSession(sessionId, WorkspaceEventCreator.MANUAL);
    }

    @PostMapping("/recordings/download")
    public void downloadSelectedRecordings(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @RequestBody SelectedRecordingsRequest request) {
        LOG.debug("Downloading selected recordings: fileCount={}",
                request.recordingIds() != null ? request.recordingIds().size() : 0);
        RecordingsDownloadManager mgr = resolver.resolve(workspaceId, projectId).projectManager().recordingsDownloadManager();
        mgr.mergeAndDownloadRecordings(request.sessionId(), request.recordingIds());
    }

    @PostMapping("/recordings/delete")
    public void deleteRecording(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @RequestBody SelectedRecordingsRequest request) {
        LOG.debug("Deleting recording from repository");
        RepositoryManager mgr = resolver.resolve(workspaceId, projectId).projectManager().repositoryManager();
        mgr.deleteFilesInSession(request.sessionId(), request.recordingIds());
    }

    @GetMapping(value = "/sessions/{sessionId}/files/{fileId}/download",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("sessionId") String sessionId,
            @PathVariable("fileId") String fileId) {

        LOG.debug("Downloading session file: sessionId={} fileId={}", sessionId, fileId);
        RepositoryManager mgr = resolver.resolve(workspaceId, projectId).projectManager().repositoryManager();
        StreamedRecordingFile file = mgr.streamFile(sessionId, fileId);

        StreamingResponseBody body = output -> {
            try (InputStream input = file.openStream()) {
                input.transferTo(output);
            } catch (IOException e) {
                throw new RuntimeException("Failed to stream file: " + file.fileName(), e);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + sessionId + "-" + file.fileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }
}
