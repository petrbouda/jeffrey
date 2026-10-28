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

import cafe.jeffrey.hub.core.volume.MountTimes;
import cafe.jeffrey.hub.core.volume.WorkspaceDirectory;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceReferenceId;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.layout.VolumeChanges;
import cafe.jeffrey.shared.volume.layout.VolumeWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * The workspaces under the hub's workspaces root: each a directory named by a valid reference id,
 * the one the provisioner writes into. Nothing in a directory is read to tell — the same rule
 * Microscope reads the volume by.
 */
public final class HubWorkspacesManager implements WorkspacesManager {

    private static final Logger LOG = LoggerFactory.getLogger(HubWorkspacesManager.class);

    private final FilesystemVolume volume;
    private final WorkspaceManager.Factory workspaceManagerFactory;

    public HubWorkspacesManager(FilesystemVolume volume, WorkspaceManager.Factory workspaceManagerFactory) {
        this.volume = volume;
        this.workspaceManagerFactory = workspaceManagerFactory;
    }

    @Override
    public WorkspaceInfo create(String referenceId) {
        WorkspaceReferenceId.validate(referenceId);
        if (!VolumeChanges.createWorkspace(volume, referenceId)) {
            throw new WorkspaceAlreadyExistsException("Workspace with reference ID '" + referenceId + "' already exists");
        }
        LOG.info("Created workspace: workspace_id={}", referenceId);
        return VolumeWorkspace.at(volume, referenceId)
                .flatMap(this::workspace)
                .orElseThrow()
                .info();
    }

    @Override
    public boolean exists(String referenceId) {
        return WorkspaceReferenceId.isValid(referenceId) && VolumeWorkspace.at(volume, referenceId).isPresent();
    }

    @Override
    public List<WorkspaceManager> findAll() {
        return VolumeWorkspace.all(volume).stream()
                .map(this::workspace)
                .flatMap(Optional::stream)
                .map(workspaceManagerFactory)
                .toList();
    }

    private Optional<WorkspaceDirectory> workspace(VolumeWorkspace workspace) {
        if (!WorkspaceReferenceId.isValid(workspace.id())) {
            return Optional.empty();
        }
        return Optional.of(new WorkspaceDirectory(
                volume, workspace.path(), MountTimes.createdAt(workspace.createdAt(), workspace.path())));
    }
}
