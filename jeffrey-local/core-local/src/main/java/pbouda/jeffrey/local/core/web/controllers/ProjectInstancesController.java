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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.local.core.manager.project.ProjectManager;
import pbouda.jeffrey.local.core.manager.project.RemoteInstancesManager;
import pbouda.jeffrey.local.core.resources.response.InstanceDetailResponse;
import pbouda.jeffrey.local.core.resources.response.InstanceResponse;
import pbouda.jeffrey.local.core.resources.response.InstanceSessionDetailResponse;
import pbouda.jeffrey.local.core.resources.response.InstanceSessionResponse;
import pbouda.jeffrey.local.core.web.ProjectManagerResolver;
import pbouda.jeffrey.shared.common.exception.Exceptions;

import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/api/internal/workspaces/{workspaceId}/projects/{projectId}/instances")
public class ProjectInstancesController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectInstancesController.class);

    private final ProjectManagerResolver resolver;
    private final Clock clock;

    public ProjectInstancesController(ProjectManagerResolver resolver, Clock clock) {
        this.resolver = resolver;
        this.clock = clock;
    }

    @GetMapping
    public List<InstanceResponse> list(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "includeSessions", defaultValue = "false") boolean includeSessions) {
        RemoteInstancesManager mgr = managerFor(workspaceId, projectId);
        var result = mgr.findAll(includeSessions).stream()
                .map(i -> InstanceResponse.from(i, clock))
                .toList();
        LOG.debug("Listed project instances: projectId={} count={} include_sessions={}",
                projectId, result.size(), includeSessions);
        return result;
    }

    @GetMapping("/{instanceId}")
    public InstanceResponse get(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("instanceId") String instanceId) {
        LOG.debug("Fetching project instance: instanceId={}", instanceId);
        return managerFor(workspaceId, projectId).find(instanceId)
                .map(i -> InstanceResponse.from(i, clock))
                .orElseThrow(() -> Exceptions.invalidRequest("Instance not found: " + instanceId));
    }

    @GetMapping("/{instanceId}/detail")
    public InstanceDetailResponse getDetail(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("instanceId") String instanceId) {
        LOG.debug("Fetching instance detail: instanceId={}", instanceId);
        return managerFor(workspaceId, projectId).detail(instanceId)
                .orElseThrow(() -> Exceptions.invalidRequest("Instance not found: " + instanceId));
    }

    @GetMapping("/{instanceId}/sessions")
    public List<InstanceSessionResponse> getSessions(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("instanceId") String instanceId) {
        var result = managerFor(workspaceId, projectId).findSessions(instanceId).stream()
                .map(s -> InstanceSessionResponse.from(s, clock))
                .toList();
        LOG.debug("Listed instance sessions: projectId={} instanceId={} count={}", projectId, instanceId, result.size());
        return result;
    }

    @GetMapping("/{instanceId}/sessions/{sessionId}/detail")
    public InstanceSessionDetailResponse getSessionDetail(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("instanceId") String instanceId,
            @PathVariable("sessionId") String sessionId) {
        LOG.debug("Fetching instance session detail: instanceId={} sessionId={}", instanceId, sessionId);
        return managerFor(workspaceId, projectId).sessionDetail(instanceId, sessionId)
                .orElseThrow(() -> Exceptions.invalidRequest(
                        "Session not found: instanceId=" + instanceId + " sessionId=" + sessionId));
    }

    private RemoteInstancesManager managerFor(String workspaceId, String projectId) {
        ProjectManager pm = resolver.resolve(workspaceId, projectId).projectManager();
        return pm.instancesManager();
    }
}
