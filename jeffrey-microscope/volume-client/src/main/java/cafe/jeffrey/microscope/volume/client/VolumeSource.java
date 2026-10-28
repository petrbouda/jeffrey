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
import cafe.jeffrey.shared.volume.ReadFanOut;
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.WritableVolume;
import cafe.jeffrey.shared.volume.layout.ProjectSessions;
import cafe.jeffrey.shared.volume.layout.ProjectTree;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The recording volume reached on its storage, as its clients see it: where its workspaces and
 * projects lie, whether it may be changed, and the one place a volume that cannot be read or
 * written becomes the remote-unavailable error. Nothing is kept here: every level is the listing of
 * its parent, and every id a directory name.
 *
 * @param volume      the storage the volume lies on, its root holding the workspaces; a
 *                    {@link WritableVolume} unless the source is read-only
 * @param description what the source says about itself, reported as its version
 * @param readOnly    whether every change is refused
 * @param fanOut      how the independent reads of one call are run
 */
public record VolumeSource(
        VolumeAccess volume,
        String description,
        boolean readOnly,
        ReadFanOut fanOut) {

    private static final VolumePath ROOT = VolumePath.root();
    private static final String READ_ONLY =
            "%s: the remote is declared read-only (azure-files.read-only=true). "
                    + "Declare it writable to make changes through Microscope.";

    public VolumeSource {
        if (volume == null || fanOut == null || description == null || description.isBlank()) {
            throw new IllegalArgumentException("A volume source needs its volume and a description");
        }
        if (!readOnly && !(volume instanceof WritableVolume)) {
            throw new IllegalArgumentException("A volume source that may be changed needs a writable volume");
        }
    }

    /** A source whose reads run one after another. */
    public VolumeSource(VolumeAccess volume, String description, boolean readOnly) {
        this(volume, description, readOnly, ReadFanOut.sequential());
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

    /** The workspace's projects, each a visible directory in it: one listing. */
    List<ProjectTree> projects(VolumePath workspaceDirectory) {
        return ProjectTree.allIn(volume, workspaceDirectory);
    }

    /** A project's directory, named by its id; empty for an id that is not one plain path element. */
    private static Optional<VolumePath> projectDirectory(ProjectScope scope) {
        return ROOT.visibleChild(scope.workspaceId()).flatMap(workspace -> workspace.visibleChild(scope.projectId()));
    }

    /** The project, or empty when no directory is there: one look at it. */
    Optional<ProjectTree> project(ProjectScope scope) {
        return projectDirectory(scope).flatMap(directory -> ProjectTree.at(volume, directory));
    }

    /**
     * The project, not looked at: for every read and change behind a request that resolved it
     * already ({@link #project}) — the one look a project-scoped request takes. A project that has
     * gone since reads as empty, and a change finds nothing to act on.
     */
    ProjectTree resolvedProject(ProjectScope scope) {
        return projectDirectory(scope)
                .map(directory -> ProjectTree.of(volume, directory))
                .orElseThrow(() -> Exceptions.projectNotFound(scope.projectId()));
    }

    ProjectSessions sessions(ProjectTree project) {
        return new ProjectSessions(project, fanOut);
    }

    /**
     * Runs a read or a change, reporting a volume that cannot be read or written as the remote
     * being unavailable, so every caller already handles it.
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
