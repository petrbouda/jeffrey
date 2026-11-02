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

package cafe.jeffrey.microscope.core.web.controllers.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.connector.api.dto.InstanceResponse;
import cafe.jeffrey.microscope.connector.api.dto.InstanceWithSessionsResponse;
import cafe.jeffrey.microscope.connector.api.manager.ConnectorInstancesManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;

import java.util.List;
import java.util.Optional;

/**
 * A connector project's instances and their recording sessions.
 */
@RestController
@RequestMapping("/api/internal/connector/workspaces/{workspaceId}/projects/{projectId}/instances")
public class ProjectInstancesController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectInstancesController.class);

    private final ProjectManagerResolver resolver;

    public ProjectInstancesController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public List<InstanceResponse> list(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId) {
        ConnectorInstancesManager mgr = resolver.resolve(workspaceId, projectId).instancesManager();
        var result = mgr.findAll().stream()
                .map(InstanceResponse::from)
                .toList();
        LOG.debug("Listed project instances: projectId={} count={}", projectId, result.size());
        return result;
    }

    /**
     * One instance with a page of its sessions, newest first, each named but not read, from one
     * read of the connector — what an instance's page shows; a session's files come when it is
     * opened. {@code before} names the session the previous page ended on, for "Load more".
     */
    @GetMapping("/{instanceId}")
    public InstanceWithSessionsResponse get(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("instanceId") String instanceId,
            @RequestParam(value = "before", required = false) String before) {
        LOG.debug("Fetching project instance: instanceId={} before={}", instanceId, before);
        return resolver.resolve(workspaceId, projectId).instancesManager().find(instanceId, Optional.ofNullable(before))
                .map(InstanceWithSessionsResponse::from)
                .orElseThrow(() -> instanceNotFound(instanceId));
    }

    private static JeffreyClientException instanceNotFound(String instanceId) {
        return Exceptions.resourceNotFound("Instance not found: " + instanceId);
    }
}
