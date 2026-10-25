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

import cafe.jeffrey.hub.core.volume.VolumeMarkerWriter;
import cafe.jeffrey.hub.core.volume.WorkspaceDirectory;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceReferenceId;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.layout.VolumeMarkers;
import cafe.jeffrey.shared.volume.layout.WorkspaceMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The workspaces under the hub's workspaces root. A workspace is a directory named by its id with
 * the hub's {@code .workspace-info.json} in it. A directory the provisioner wrote into without one
 * is a workspace too when {@code autoCreate} is on, named by its id; otherwise it is left out
 * until someone creates the workspace.
 */
public final class HubWorkspacesManager implements WorkspacesManager {

    private static final Logger LOG = LoggerFactory.getLogger(HubWorkspacesManager.class);

    private final FilesystemVolume volume;
    private final Clock clock;
    private final boolean autoCreate;
    private final WorkspaceManager.Factory workspaceManagerFactory;

    public HubWorkspacesManager(
            FilesystemVolume volume,
            Clock clock,
            boolean autoCreate,
            WorkspaceManager.Factory workspaceManagerFactory) {

        this.volume = volume;
        this.clock = clock;
        this.autoCreate = autoCreate;
        this.workspaceManagerFactory = workspaceManagerFactory;
    }

    @Override
    public WorkspaceInfo create(CreateWorkspaceRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Workspace Name cannot be null or empty");
        }
        String id = request.referenceId().trim();
        String name = request.name().trim();

        VolumePath directory = VolumePath.root().resolve(id);
        if (VolumeMarkers.read(volume, directory, WorkspaceMarker.FILE_NAME, WorkspaceMarker.class).isPresent()) {
            throw new WorkspaceAlreadyExistsException("Workspace with reference ID '" + id + "' already exists");
        }
        // Another workspace's name; the one being adopted may already be listed under its id
        boolean nameTaken = workspaceDirectories().stream()
                .anyMatch(workspace -> !workspace.id().equals(id) && workspace.name().equals(name));
        if (nameTaken) {
            throw new WorkspaceAlreadyExistsException("Workspace with name '" + name + "' already exists");
        }

        FileSystemUtils.createDirectories(volume.resolve(directory));
        VolumeMarkerWriter.write(volume.resolve(directory), WorkspaceMarker.FILE_NAME, new WorkspaceMarker(name, clock.millis()));
        LOG.info("Created workspace: workspace_id={} name={}", id, name);
        return workspace(directory).orElseThrow().info();
    }

    @Override
    public List<WorkspaceManager> findAll() {
        return workspaceDirectories().stream()
                .map(workspaceManagerFactory)
                .toList();
    }

    private List<WorkspaceDirectory> workspaceDirectories() {
        return VolumeMarkers.childDirectories(volume, VolumePath.root()).stream()
                .map(this::workspace)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public boolean isDescribed(String workspaceId) {
        return WorkspaceReferenceId.isValid(workspaceId)
                && VolumeMarkers.read(volume, VolumePath.root().resolve(workspaceId), WorkspaceMarker.FILE_NAME,
                        WorkspaceMarker.class).isPresent();
    }

    @Override
    public Optional<WorkspaceManager> findById(String workspaceId) {
        if (!WorkspaceReferenceId.isValid(workspaceId)) {
            return Optional.empty();
        }
        VolumePath directory = VolumePath.root().resolve(workspaceId);
        if (!VolumeMarkers.isDirectory(volume, directory)) {
            return Optional.empty();
        }
        return workspace(directory).map(workspaceManagerFactory);
    }

    private Optional<WorkspaceDirectory> workspace(VolumePath directory) {
        String id = directory.name();
        if (!WorkspaceReferenceId.isValid(id)) {
            return Optional.empty();
        }
        Optional<WorkspaceMarker> marker =
                VolumeMarkers.read(volume, directory, WorkspaceMarker.FILE_NAME, WorkspaceMarker.class);
        if (marker.isEmpty() && !autoCreate) {
            return Optional.empty();
        }
        String name = marker.map(WorkspaceMarker::name).orElse(id);
        Instant createdAt = marker.map(found -> Instant.ofEpochMilli(found.createdAt()))
                .orElseGet(() -> createdAt(directory));
        return Optional.of(new WorkspaceDirectory(volume, directory, name, createdAt));
    }

    /** The directory's own creation time, for a workspace no marker describes. */
    private Instant createdAt(VolumePath directory) {
        return volume.stat(directory)
                .flatMap(VolumeEntry::createdAt)
                .orElseThrow(() -> new UncheckedIOException(
                        "Workspace directory vanished while it was read: path=" + directory,
                        new NoSuchFileException(directory.toString())));
    }
}
