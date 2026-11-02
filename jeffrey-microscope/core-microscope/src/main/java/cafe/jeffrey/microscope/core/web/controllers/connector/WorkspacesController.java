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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.core.web.dto.response.WorkspaceResponse;
import cafe.jeffrey.microscope.core.web.dto.workspace.Mappers;

import java.util.List;

/**
 * Lists and deletes the connector's workspaces (the provisioner creates them).
 */
@RestController
@RequestMapping("/api/internal/connector/workspaces")
public class WorkspacesController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspacesController.class);

    private final ProjectManagerResolver resolver;

    public WorkspacesController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public List<WorkspaceResponse> workspaces() {
        List<WorkspaceResponse> result = resolver.resolveConnector().workspaces().stream()
                .map(Mappers::toResponse)
                .toList();
        LOG.debug("Listed workspaces: count={}", result.size());
        return result;
    }

    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> delete(
            @PathVariable("workspaceId") String workspaceId) {

        resolver.resolveWorkspace(workspaceId).delete();
        return ResponseEntity.noContent().build();
    }
}
