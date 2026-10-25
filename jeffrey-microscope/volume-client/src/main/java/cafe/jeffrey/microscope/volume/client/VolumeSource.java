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

package cafe.jeffrey.microscope.volume.client;

import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.shared.common.exception.JeffreyInternalException;
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.WritableVolume;
import cafe.jeffrey.shared.volume.layout.ProjectSessions;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.VolumeMarkers;
import cafe.jeffrey.shared.volume.liveness.SessionLiveness;

import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A recording volume reached straight on its storage, as its clients see it: where its workspaces
 * and projects lie, whether it may be changed, and the one place a volume that cannot be read or
 * written becomes the error a hub's clients raise for an unreachable hub. Nothing is cached; every
 * call reads the volume again, as the hub does.
 *
 * @param volume      the storage the volume lies on, its root holding the workspaces; a
 *                    {@link WritableVolume} unless the source is read-only
 * @param liveness    how a session's liveness files are read, with this machine's clock
 * @param clock       when a change happened, for the markers a change writes
 * @param description what the source says about itself where a hub would report its version
 * @param readOnly    whether every change is refused
 */
public record VolumeSource(
        VolumeAccess volume, SessionLiveness liveness, Clock clock, String description, boolean readOnly) {

    private static final VolumePath ROOT = VolumePath.root();
    private static final String READ_ONLY =
            "%s: this source is declared read-only (azure-files.read-only=true). "
                    + "Make the change through the Jeffrey Hub that maintains the volume, or declare the source writable.";

    public VolumeSource {
        if (volume == null || liveness == null || clock == null || description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "A volume source needs its volume, its liveness, a clock and a description");
        }
        if (!readOnly && !(volume instanceof WritableVolume)) {
            throw new IllegalArgumentException("A volume source that may be changed needs a writable volume");
        }
    }

    /**
     * The volume to make {@code change} on.
     *
     * @throws JeffreyClientException the read-only refusal, naming the change
     */
    WritableVolume writable(String change) {
        if (readOnly) {
            throw Exceptions.invalidRequest(READ_ONLY.formatted(change));
        }
        return (WritableVolume) volume;
    }

    /** The visible directories at the volume's root: every one is a workspace, named by its marker or its id. */
    List<VolumePath> workspaceDirectories() {
        return VolumeMarkers.childDirectories(volume, ROOT);
    }

    Optional<VolumePath> workspaceDirectory(String workspaceId) {
        return ROOT.visibleChild(workspaceId).filter(directory -> VolumeMarkers.isDirectory(volume, directory));
    }

    /** The workspace's projects, each a directory whose project marker reads. */
    List<ProjectTree> projects(VolumePath workspaceDirectory) {
        return VolumeMarkers.childDirectories(volume, workspaceDirectory).stream()
                .map(directory -> ProjectTree.at(volume, directory))
                .flatMap(Optional::stream)
                .toList();
    }

    Optional<ProjectTree> project(ProjectScope scope) {
        return workspaceDirectory(scope.workspaceId()).flatMap(workspace -> projects(workspace).stream()
                .filter(project -> project.marker().projectId().equals(scope.projectId()))
                .findFirst());
    }

    ProjectTree requireProject(ProjectScope scope) {
        return project(scope).orElseThrow(() -> Exceptions.projectNotFound(scope.projectId()));
    }

    ProjectSessions sessions(ProjectTree project) {
        return new ProjectSessions(project, liveness);
    }

    /**
     * Runs a read or a change, reporting a volume that cannot be read or written the way a hub's
     * clients report a hub that cannot be reached, so every caller already handles it.
     */
    <T> T read(Supplier<T> read) {
        try {
            return read.get();
        } catch (UncheckedIOException e) {
            throw new JeffreyInternalException(ErrorCode.REMOTE_UNAVAILABLE,
                    "Cannot reach the recording volume: source=%s reason=%s".formatted(description, e.getMessage()), e);
        }
    }

    void read(Runnable read) {
        read(() -> {
            read.run();
            return null;
        });
    }
}
