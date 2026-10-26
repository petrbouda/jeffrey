/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.shared.ui.remote.controller;

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
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionResponse;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.remote.client.dto.RepositoryStatisticsResponse;
import cafe.jeffrey.microscope.remote.client.manager.RepositoryManager;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.microscope.model.repository.StreamedFile;
import cafe.jeffrey.shared.ui.remote.bridge.RemoteProjectAccess;
import cafe.jeffrey.shared.ui.remote.request.SessionRetainedRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;

/**
 * Shared remote-project repository controller. Annotated @RestController and registered as a @Bean via RemoteFeatureConfiguration;
 * it lives outside both apps' component-scan roots, so it is mapped exactly once (by the @Bean).
 */
@RestController
@RequestMapping("/api/internal/remote/workspaces/{workspaceId}/projects/{projectId}/repository")
public class ProjectRepositoryController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectRepositoryController.class);

    /**
     * Between the session's id and the file's name in a downloaded file's name: a file straight off
     * the remote belongs to no bundle yet, and every session names its heap dump alike.
     */
    private static final String SESSION_FILE_SEPARATOR = "-";

    private final RemoteProjectAccess projectAccess;
    private final Clock clock;

    public ProjectRepositoryController(RemoteProjectAccess projectAccess, Clock clock) {
        this.projectAccess = projectAccess;
        this.clock = clock;
    }

    @GetMapping("/statistics")
    public RepositoryStatisticsResponse getRepositoryStatistics(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        LOG.debug("Fetching repository statistics: projectId={}", projectId);
        RepositoryStatistics stats = projectAccess.repositoryManager(workspaceId, projectId)
                .repositoryStatistics();
        return RepositoryStatisticsResponse.from(stats);
    }

    /**
     * One session with its files — what the session list fetches when a session is expanded, so
     * the listing itself never carries every file of every session.
     */
    @GetMapping("/sessions/{sessionId}")
    public RecordingSessionResponse getRepositorySession(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("sessionId") String sessionId) {
        LOG.debug("Fetching repository session: projectId={} sessionId={}", projectId, sessionId);
        RecordingSession session = projectAccess.repositoryManager(workspaceId, projectId)
                .recordingSession(sessionId);
        return RecordingSessionResponse.from(session, clock);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public void deleteSession(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("sessionId") String sessionId) {
        LOG.debug("Deleting repository session: sessionId={}", sessionId);
        RepositoryManager mgr = projectAccess.repositoryManager(workspaceId, projectId);
        mgr.deleteRecordingSession(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/retained")
    public void setSessionRetained(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("sessionId") String sessionId,
            @RequestBody SessionRetainedRequest request) {
        LOG.debug("Updating repository session retention: sessionId={} retained={}", sessionId, request.retained());
        RepositoryManager mgr = projectAccess.repositoryManager(workspaceId, projectId);
        mgr.setSessionRetained(sessionId, request.retained());
    }

    @GetMapping(value = "/sessions/{sessionId}/files/{fileId}/download",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("sessionId") String sessionId,
            @PathVariable("fileId") String fileId) {

        LOG.debug("Downloading session file: sessionId={} fileId={}", sessionId, fileId);
        RepositoryManager mgr = projectAccess.repositoryManager(workspaceId, projectId);
        StreamedFile file = mgr.streamFile(sessionId, fileId);

        StreamingResponseBody body = output -> {
            try (InputStream input = file.openStream()) {
                input.transferTo(output);
            } catch (IOException e) {
                throw new RuntimeException("Failed to stream file: " + file.fileName(), e);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        DownloadHeaders.attachment(sessionId + SESSION_FILE_SEPARATOR + file.fileName()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }
}
