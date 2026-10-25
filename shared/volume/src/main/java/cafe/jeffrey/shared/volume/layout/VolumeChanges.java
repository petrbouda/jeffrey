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
 * The changes a user asks of the volume — describing a workspace, pinning a session, deleting a
 * session — with the rules each one keeps, in one place for every process that may write it:
 * the hub on its mount, Microscope on a share it reaches directly. Removing a workspace's or a
 * project's whole directory needs no rule and is {@link WritableVolume#delete} itself.
 *
 * <p>Upkeep (retention, compression) is not here: that stays with the hub that owns the volume.
 */
public final class VolumeChanges {

    private static final byte[] EMPTY_FLAG = new byte[0];

    private VolumeChanges() {
    }

    /** Whether a workspace's directory already holds its {@code .workspace-info.json}. */
    public static boolean isDescribedWorkspace(VolumeAccess volume, VolumePath directory) {
        return VolumeMarkers.read(volume, directory, WorkspaceMarker.FILE_NAME, WorkspaceMarker.class).isPresent();
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
     * Pins a session against retention, or releases it: the {@code .retained} flag file.
     *
     * @return false when the project holds no such session
     */
    public static boolean setRetained(WritableVolume volume, ProjectTree project, String sessionId, boolean retained) {
        Optional<SessionDirectory> session = project.session(sessionId);
        if (session.isEmpty()) {
            return false;
        }
        VolumePath flag = session.get().path().resolve(SessionDirectory.RETAINED_FLAG);
        if (retained) {
            volume.write(flag, EMPTY_FLAG);
        } else {
            volume.delete(flag);
        }
        return true;
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
