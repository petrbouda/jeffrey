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

package cafe.jeffrey.hub.core.manager.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import cafe.jeffrey.hub.persistence.api.WorkspacesRepository;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceStatus;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

public final class HubWorkspacesManager implements WorkspacesManager {

    private static final Logger LOG = LoggerFactory.getLogger(HubWorkspacesManager.class);

    private final Clock clock;
    private final WorkspacesRepository workspacesRepository;
    private final WorkspaceManager.Factory workspaceManagerFactory;

    public HubWorkspacesManager(
            Clock clock,
            WorkspacesRepository workspacesRepository,
            WorkspaceManager.Factory workspaceManagerFactory) {

        this.clock = clock;
        this.workspacesRepository = workspacesRepository;
        this.workspaceManagerFactory = workspaceManagerFactory;
    }

    @Override
    public WorkspaceInfo create(CreateWorkspaceRequest request) {
        LOG.debug("Creating workspace: name={}", request.name());
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Workspace Name cannot be null or empty");
        }

        String trimmedSourceId = request.referenceId().trim();
        String trimmedName = request.name().trim();

        if (findByReferenceId(trimmedSourceId).isPresent()) {
            throw new WorkspaceAlreadyExistsException(
                    "Workspace with reference ID '" + trimmedSourceId + "' already exists");
        }

        if (workspacesRepository.existsByName(trimmedName)) {
            throw new WorkspaceAlreadyExistsException(
                    "Workspace with name '" + trimmedName + "' already exists");
        }

        WorkspaceInfo workspaceInfo = new WorkspaceInfo(
                null,
                trimmedSourceId,
                trimmedSourceId,
                trimmedName,
                null,
                null,
                clock.instant(),
                WorkspaceStatus.UNKNOWN,
                0
        );

        try {
            return workspacesRepository.create(workspaceInfo);
        } catch (DuplicateKeyException e) {
            // The checks above raced another creator — the reconciler's auto-create and a
            // client request on the same reference id — and the row is the other one's
            throw new WorkspaceAlreadyExistsException(
                    "Workspace with reference ID '" + trimmedSourceId + "' or name '" + trimmedName + "' already exists");
        }
    }

    @Override
    public List<WorkspaceManager> findAll() {
        return workspacesRepository.findAll().stream()
                .<WorkspaceManager>map(workspaceManagerFactory)
                .toList();
    }

    @Override
    public Optional<WorkspaceManager> findById(String workspaceId) {
        return workspacesRepository.find(workspaceId)
                .map(workspaceManagerFactory);
    }

    @Override
    public Optional<WorkspaceManager> findByReferenceId(String referenceId) {
        return workspacesRepository.findByReferenceId(referenceId)
                .map(workspaceManagerFactory);
    }
}
