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
import cafe.jeffrey.shared.ui.remote.bridge.RemoteBrowserAccess;
import cafe.jeffrey.shared.ui.remote.dto.WorkspaceResponse;
import cafe.jeffrey.shared.ui.remote.request.CreateWorkspaceRequest;

import java.util.List;

/**
 * Shared WorkspaceBrowser controller listing/creating/deleting the remote's workspaces, registered via
 * {@code RemoteFeatureConfiguration} over a {@link RemoteBrowserAccess} bridge.
 */
@RestController
@RequestMapping("/api/internal/remote/workspaces")
public class WorkspacesController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspacesController.class);

    private final RemoteBrowserAccess access;

    public WorkspacesController(RemoteBrowserAccess access) {
        this.access = access;
    }

    @GetMapping
    public List<WorkspaceResponse> workspaces() {
        List<WorkspaceResponse> result = access.workspaces();
        LOG.debug("Listed workspaces: count={}", result.size());
        return result;
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse workspace(
            @PathVariable("workspaceId") String workspaceId) {

        return access.workspace(workspaceId);
    }

    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> delete(
            @PathVariable("workspaceId") String workspaceId) {

        access.deleteWorkspace(workspaceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(
            @RequestBody CreateWorkspaceRequest request) {

        if (request.referenceId() == null || request.referenceId().isBlank()) {
            throw Exceptions.invalidRequest("Reference ID is required");
        }
        String referenceId = request.referenceId().trim();
        if (!WorkspaceReferenceId.isValid(referenceId)) {
            throw Exceptions.invalidRequest(
                    "Invalid workspace reference ID: " + WorkspaceReferenceId.DESCRIPTION);
        }

        WorkspaceResponse created = access.createWorkspace(referenceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
