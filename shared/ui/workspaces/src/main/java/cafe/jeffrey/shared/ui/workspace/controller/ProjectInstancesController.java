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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.hub.client.dto.InstanceDetailResponse;
import cafe.jeffrey.hub.client.dto.InstanceResponse;
import cafe.jeffrey.hub.client.dto.InstanceSessionDetailResponse;
import cafe.jeffrey.hub.client.dto.InstanceSessionResponse;
import cafe.jeffrey.hub.client.manager.RemoteInstancesManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.ui.workspace.bridge.RemoteProjectAccess;

import java.time.Clock;
import java.util.List;

/**
 * Shared remote-project instances controller. Annotated @RestController and registered as a @Bean via WorkspacesFeatureConfiguration;
 * it lives outside both apps' component-scan roots, so it is mapped exactly once (by the @Bean).
 */
@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}/projects/{projectId}/instances")
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
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "includeSessions", defaultValue = "false") boolean includeSessions) {
        RemoteInstancesManager mgr = projectAccess.instancesManager(hubId, workspaceId, projectId);
        var result = mgr.findAll(includeSessions).stream()
                .map(i -> InstanceResponse.from(i, clock))
                .toList();
        LOG.debug("Listed project instances: projectId={} count={} include_sessions={}",
                projectId, result.size(), includeSessions);
        return result;
    }

    @GetMapping("/{instanceId}")
    public InstanceResponse get(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("instanceId") String instanceId) {
        LOG.debug("Fetching project instance: instanceId={}", instanceId);
        return projectAccess.instancesManager(hubId, workspaceId, projectId).find(instanceId)
                .map(i -> InstanceResponse.from(i, clock))
                .orElseThrow(() -> Exceptions.invalidRequest("Instance not found: " + instanceId));
    }

    @GetMapping("/{instanceId}/detail")
    public InstanceDetailResponse getDetail(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("instanceId") String instanceId) {
        LOG.debug("Fetching instance detail: instanceId={}", instanceId);
        return projectAccess.instancesManager(hubId, workspaceId, projectId).detail(instanceId)
                .orElseThrow(() -> Exceptions.invalidRequest("Instance not found: " + instanceId));
    }

    @GetMapping("/{instanceId}/sessions")
    public List<InstanceSessionResponse> getSessions(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("instanceId") String instanceId) {
        var result = projectAccess.instancesManager(hubId, workspaceId, projectId).findSessions(instanceId).stream()
                .map(s -> InstanceSessionResponse.from(s, clock))
                .toList();
        LOG.debug("Listed instance sessions: projectId={} instanceId={} count={}", projectId, instanceId, result.size());
        return result;
    }

    @GetMapping("/{instanceId}/sessions/{sessionId}/detail")
    public InstanceSessionDetailResponse getSessionDetail(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("instanceId") String instanceId,
            @PathVariable("sessionId") String sessionId) {
        LOG.debug("Fetching instance session detail: instanceId={} sessionId={}", instanceId, sessionId);
        return projectAccess.instancesManager(hubId, workspaceId, projectId).sessionDetail(instanceId, sessionId)
                .orElseThrow(() -> Exceptions.invalidRequest(
                        "Session not found: instanceId=" + instanceId + " sessionId=" + sessionId));
    }
}
