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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.remote.client.dto.InstanceDetailResponse;
import cafe.jeffrey.microscope.remote.client.dto.InstanceResponse;
import cafe.jeffrey.microscope.remote.client.dto.InstanceSessionDetailResponse;
import cafe.jeffrey.microscope.remote.client.dto.InstanceWithSessionsResponse;
import cafe.jeffrey.microscope.remote.client.manager.RemoteInstancesManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.shared.ui.remote.bridge.RemoteProjectAccess;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Shared remote-project instances controller. Annotated @RestController and registered as a @Bean via RemoteFeatureConfiguration;
 * it lives outside both apps' component-scan roots, so it is mapped exactly once (by the @Bean).
 */
@RestController
@RequestMapping("/api/internal/remote/workspaces/{workspaceId}/projects/{projectId}/instances")
public class ProjectInstancesController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectInstancesController.class);

    private final RemoteProjectAccess projectAccess;
    private final Clock clock;

    public ProjectInstancesController(RemoteProjectAccess projectAccess, Clock clock) {
        this.projectAccess = projectAccess;
        this.clock = clock;
    }

    @GetMapping
    public List<InstanceResponse> list(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "includeSessions", defaultValue = "false") boolean includeSessions) {
        RemoteInstancesManager mgr = projectAccess.instancesManager(workspaceId, projectId);
        var result = mgr.findAll(includeSessions).stream()
                .map(i -> InstanceResponse.from(i, clock))
                .toList();
        LOG.debug("Listed project instances: projectId={} count={} include_sessions={}",
                projectId, result.size(), includeSessions);
        return result;
    }

    /**
     * One instance with a page of its sessions, newest first and each with its files, from one read
     * of the remote — what an instance's page shows, opening a session included; {@code before}
     * names the session the previous page ended on, for "Show older sessions".
     */
    @GetMapping("/{instanceId}")
    public InstanceWithSessionsResponse get(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("instanceId") String instanceId,
            @RequestParam(value = "before", required = false) String before) {
        LOG.debug("Fetching project instance: instanceId={} before={}", instanceId, before);
        return projectAccess.instancesManager(workspaceId, projectId).find(instanceId, Optional.ofNullable(before))
                .map(found -> InstanceWithSessionsResponse.from(found, clock))
                .orElseThrow(() -> instanceNotFound(instanceId));
    }

    @GetMapping("/{instanceId}/detail")
    public InstanceDetailResponse getDetail(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("instanceId") String instanceId) {
        LOG.debug("Fetching instance detail: instanceId={}", instanceId);
        return projectAccess.instancesManager(workspaceId, projectId).detail(instanceId)
                .orElseThrow(() -> instanceNotFound(instanceId));
    }

    @GetMapping("/{instanceId}/sessions/{sessionId}/detail")
    public InstanceSessionDetailResponse getSessionDetail(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("instanceId") String instanceId,
            @PathVariable("sessionId") String sessionId) {
        LOG.debug("Fetching instance session detail: instanceId={} sessionId={}", instanceId, sessionId);
        return projectAccess.instancesManager(workspaceId, projectId).sessionDetail(instanceId, sessionId)
                .orElseThrow(() -> Exceptions.resourceNotFound(
                        "Session not found: instanceId=" + instanceId + " sessionId=" + sessionId));
    }

    private static JeffreyClientException instanceNotFound(String instanceId) {
        return Exceptions.resourceNotFound("Instance not found: " + instanceId);
    }
}
