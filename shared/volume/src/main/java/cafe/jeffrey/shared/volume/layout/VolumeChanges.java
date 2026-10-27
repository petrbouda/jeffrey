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

package cafe.jeffrey.shared.volume.layout;

import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.WritableVolume;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The changes a user asks of the volume — creating a workspace, deleting a session — with the
 * rules each one keeps, in one place for every process that may write it:
 * the hub on its mount, Microscope on a share it reaches directly. Removing a workspace's or a
 * project's whole directory needs no rule and is {@link WritableVolume#delete} itself.
 *
 * <p>Upkeep (retention, compression) is not here: that stays with the hub that owns the volume.
 */
public final class VolumeChanges {

    private VolumeChanges() {
    }

    /** Whether a workspace's directory already holds its {@code .workspace-info.json}. */
    public static boolean isDescribedWorkspace(VolumeAccess volume, VolumePath directory) {
        return VolumeMarkers.read(volume, directory, WorkspaceMarker.FILE_NAME, WorkspaceMarker.class).isPresent();
    }

    /**
     * Creates the workspace {@code id} named {@code name}, adopting a directory the provisioner
     * already wrote into; refused for an id already described or a name another workspace goes by.
     *
     * @param createdAt epoch millis, for its marker
     */
    public static WorkspaceCreation createWorkspace(WritableVolume volume, String id, String name, long createdAt) {
        VolumePath directory = VolumePath.root().resolve(id);
        if (isDescribedWorkspace(volume, directory)) {
            return WorkspaceCreation.ID_TAKEN;
        }
        // Another workspace's name; the one being adopted may already be listed under its id
        boolean nameTaken = VolumeWorkspace.all(volume).stream()
                .anyMatch(workspace -> !workspace.id().equals(id) && workspace.name().equals(name));
        if (nameTaken) {
            return WorkspaceCreation.NAME_TAKEN;
        }
        describeWorkspace(volume, directory, new WorkspaceMarker(name, createdAt));
        return WorkspaceCreation.CREATED;
    }

    /**
     * Creates a workspace's directory, adopting one the provisioner already wrote into, and
     * describes it with its marker — written whole, so a reader alongside sees it or not at all.
     */
    public static void describeWorkspace(WritableVolume volume, VolumePath directory, WorkspaceMarker marker) {
        volume.createDirectories(directory);
        volume.write(directory.resolve(WorkspaceMarker.FILE_NAME), Json.toString(marker).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Deletes a session; its instance goes too when no directory is left in it. Any directory
     * counts, marker or not: one the provisioner has created but not yet described is a session
     * about to start, and its profiler may already be writing into it.
     */
    public static SessionDeletion deleteSession(WritableVolume volume, ProjectTree project, String sessionId) {
        Optional<SessionDirectory> session = project.session(sessionId);
        if (session.isEmpty()) {
            return SessionDeletion.NOT_FOUND;
        }
        VolumePath instanceDir = session.get().path().parent();
        volume.delete(session.get().path());
        if (project.holdsAnyDirectory(instanceDir)) {
            return SessionDeletion.SESSION_DELETED;
        }
        volume.delete(instanceDir);
        return SessionDeletion.SESSION_AND_INSTANCE_DELETED;
    }
}
