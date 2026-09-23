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

package cafe.jeffrey.shared.ui.hub.controller;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.microscope.model.workspace.WorkspaceReferenceId;
import cafe.jeffrey.shared.ui.hub.bridge.HubBrowserAccess;
import cafe.jeffrey.shared.ui.hub.dto.WorkspaceResponse;
import cafe.jeffrey.shared.ui.hub.request.CreateWorkspaceRequest;

import java.util.List;

/**
 * Shared WorkspaceBrowser controller listing/creating/deleting a hub's workspaces. Both deployments
 * register it via {@code HubsFeatureConfiguration} and supply a {@link HubBrowserAccess}
 * bridge. Microscope-only capabilities (e.g. workspace events) live in deployment-specific
 * supplementary controllers.
 */
@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces")
public class WorkspacesController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspacesController.class);

    private final HubBrowserAccess access;

    public WorkspacesController(HubBrowserAccess access) {
        this.access = access;
    }

    @GetMapping
    public List<WorkspaceResponse> workspaces(@PathVariable("hubId") String hubId) {
        List<WorkspaceResponse> result = access.workspaces(hubId);
        LOG.debug("Listed workspaces: hubId={} count={}", hubId, result.size());
        return result;
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse workspace(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId) {

        return access.workspace(hubId, workspaceId);
    }

    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> delete(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId) {

        access.deleteWorkspace(hubId, workspaceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(
            @PathVariable("hubId") String hubId,
            @RequestBody CreateWorkspaceRequest request) {

        if (request.referenceId() == null || request.referenceId().isBlank()) {
            throw Exceptions.invalidRequest("Reference ID is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw Exceptions.invalidRequest("Workspace name is required");
        }
        String referenceId = request.referenceId().trim();
        if (!WorkspaceReferenceId.isValid(referenceId)) {
            throw Exceptions.invalidRequest(
                    "Invalid workspace reference ID: " + WorkspaceReferenceId.DESCRIPTION);
        }

        try {
            WorkspaceResponse created = access.createWorkspace(hubId, referenceId, request.name().trim());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (StatusRuntimeException e) {
            throw mapStatusError(e);
        }
    }

    private static RuntimeException mapStatusError(StatusRuntimeException e) {
        if (e.getStatus().getCode() == Status.Code.ALREADY_EXISTS) {
            return Exceptions.invalidRequest(
                    e.getStatus().getDescription() != null
                            ? e.getStatus().getDescription()
                            : "Workspace already exists on this hub");
        }
        if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
            return Exceptions.invalidRequest(
                    e.getStatus().getDescription() != null
                            ? e.getStatus().getDescription()
                            : "Invalid workspace creation request");
        }
        return e;
    }
}
