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

import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.WritableVolume;

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

    /**
     * Creates the workspace {@code id}: its directory, which is all a workspace is. False when a
     * directory by that id is there already — the provisioner may have written into it first.
     */
    public static boolean createWorkspace(WritableVolume volume, String id) {
        VolumePath directory = VolumePath.root().resolve(id);
        if (VolumeDirectories.directory(volume, directory).isPresent()) {
            return false;
        }
        volume.createDirectories(directory);
        return true;
    }

    /**
     * Deletes a session; its instance goes too when no directory is left in it. Any directory
     * counts: one the provisioner has just created is a session about to start, and its profiler
     * may already be writing into it.
     */
    public static SessionDeletion deleteSession(WritableVolume volume, ProjectTree project, String sessionId) {
        Optional<SessionHeader> session = project.session(sessionId);
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
