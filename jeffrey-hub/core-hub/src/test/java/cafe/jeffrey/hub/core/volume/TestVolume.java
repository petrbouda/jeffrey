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

package cafe.jeffrey.hub.core.volume;

import cafe.jeffrey.hub.core.project.repository.FilesystemRepositoryStorage;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.VolumeChanges;
import cafe.jeffrey.shared.volume.layout.WorkspaceMarker;
import cafe.jeffrey.test.volume.VolumeWriter;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Writes a volume the way the provisioner, the profiler and the hub's own workspace creation do,
 * so tests read what production reads: directories named for what they hold, and recordings.
 */
public final class TestVolume {

    private TestVolume() {
    }

    /** Where {@code path}, a directory under {@code root}, lies on the volume rooted there. */
    public static VolumePath volumePath(Path root, Path path) {
        VolumePath volumePath = VolumePath.root();
        for (Path element : root.relativize(path)) {
            volumePath = volumePath.resolve(element.toString());
        }
        return volumePath;
    }

    /** The hub's storage of the project in {@code projectDir}, on the volume rooted at {@code workspacesRoot}. */
    public static FilesystemRepositoryStorage storage(ProjectInfo info, Path workspacesRoot, Path projectDir) {
        FilesystemVolume volume = new FilesystemVolume(workspacesRoot);
        ProjectTree tree = ProjectTree.at(volume, volumePath(workspacesRoot, projectDir)).orElseThrow();
        return new FilesystemRepositoryStorage(info, tree, volume);
    }

    public static Path workspace(Path workspacesRoot, String id, String name, Instant createdAt) {
        VolumeChanges.describeWorkspace(new FilesystemVolume(workspacesRoot), VolumePath.root().resolve(id),
                new WorkspaceMarker(name, createdAt.toEpochMilli()));
        return workspacesRoot.resolve(id);
    }

    public static Path project(Path workspaceDir, String name) {
        return VolumeWriter.project(workspaceDir, name);
    }

    public static Path instance(Path projectDir, String instanceId) {
        return VolumeWriter.instance(projectDir, instanceId);
    }

    /** A session started at {@code start}, named as the provisioner names it. */
    public static Path session(Path instanceDir, Instant start) {
        return VolumeWriter.session(instanceDir, start);
    }

    /** The id a session started at {@code start} goes by. */
    public static String sessionId(Instant start) {
        return VolumeWriter.sessionIdAt(start);
    }

    /** The name a recording opened at {@code openedAt} on a UTC machine has. */
    public static String recordingName(Instant openedAt) {
        return VolumeWriter.recordingName(openedAt, ZoneOffset.UTC);
    }

    public static Path file(Path dir, String name, String content) {
        return VolumeWriter.file(dir, name, content);
    }
}
