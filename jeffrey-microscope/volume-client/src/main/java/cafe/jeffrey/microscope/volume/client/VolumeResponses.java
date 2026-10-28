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

import cafe.jeffrey.microscope.remote.client.dto.InstanceResponse;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionHeaderResponse;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionResponse;
import cafe.jeffrey.microscope.remote.client.dto.RemoteProjectResponse;
import cafe.jeffrey.microscope.model.repository.RecordedSpan;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.microscope.model.repository.SessionSize;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;
import cafe.jeffrey.shared.volume.layout.InstanceDirectory;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.SessionFile;
import cafe.jeffrey.shared.volume.layout.SessionHeader;
import cafe.jeffrey.shared.volume.layout.VolumeSession;

import java.time.Instant;
import java.util.List;

/**
 * The volume's types onto Microscope's own. What the hub's jobs decide from the same volume
 * (whether a session failed, which recording may still be on its way) is decided here by the same
 * rules, so Microscope and the hub never disagree about a session.
 */
final class VolumeResponses {

    private VolumeResponses() {
    }

    /** A session as a listing carries it: its file count and size, none of its files. */
    static RecordingSessionResponse header(ProjectTree project, VolumeSession session) {
        return response(project, session, false);
    }

    /** A session read on its own, with every file. */
    static RecordingSessionResponse withFiles(ProjectTree project, VolumeSession session) {
        return response(project, session, true);
    }

    /**
     * The session in Microscope's model without its files, enough for a filter to judge: its span
     * from the names its listing shows, no file looked at.
     */
    static RecordingSession unlisted(VolumeSession session, List<Instant> recordingStarts) {
        return new RecordingSession(
                session.id(),
                session.name(),
                session.instanceId(),
                session.createdAt(),
                RecordedSpan.end(recordingStarts).orElse(null),
                session.newest(),
                null,
                List.of(),
                false,
                new SessionSize(0, 0));
    }

    /** An instance as its directory names it. */
    static InstanceResponse instance(InstanceDirectory instance) {
        return InstanceResponse.summary(instance.id(), instance.createdAt().map(Instant::toEpochMilli).orElse(null));
    }

    /** A session as its instance's listing names it: nothing of it read. */
    static RecordingSessionHeaderResponse header(SessionHeader session) {
        return new RecordingSessionHeaderResponse(
                session.id(),
                session.name(),
                session.instanceId(),
                session.createdAt().toEpochMilli(),
                session.newest());
    }

    /** A project as its directory names it; its size is never measured here, only by the hub's retention pass. */
    static RemoteProjectResponse project(ProjectTree project, String workspaceId) {
        return new RemoteProjectResponse(
                project.id(),
                project.id(),
                project.createdAt().orElse(null),
                workspaceId,
                RepositoryStatistics.NOT_YET_MEASURED);
    }

    /**
     * @param name      the name its marker gives it, or its id for a directory without one
     * @param createdAt from its marker, or the directory's own creation time
     */
    static WorkspaceInfo workspace(String id, String name, Instant createdAt) {
        return new WorkspaceInfo(id, id, null, name, null, null, createdAt, WorkspaceStatus.AVAILABLE);
    }

    /**
     * The session in Microscope's model, then on the wire: its span from the names its listing
     * shows, its size from its files, and those files themselves only when it carries them.
     */
    private static RecordingSessionResponse response(ProjectTree project, VolumeSession session, boolean carryFiles) {
        List<SessionFile> files = project.files(session);
        long bytes = files.stream().mapToLong(SessionFile::size).sum();
        RecordingSession described = new RecordingSession(
                session.id(),
                session.name(),
                session.instanceId(),
                session.createdAt(),
                RecordedSpan.end(project.recordingStarts(session)).orElse(null),
                session.newest(),
                null,
                carryFiles ? files.stream().map(VolumeResponses::file).toList() : List.of(),
                failed(session, bytes),
                new SessionSize(files.size(), bytes));
        return RecordingSessionResponse.from(described);
    }

    /**
     * A session a newer one ended before it produced any data — a crash before the first chunk.
     * The newest session is never failed: it may simply not have written anything yet. The rule
     * the hub's retention applies too.
     */
    private static boolean failed(VolumeSession session, long bytes) {
        return !session.newest() && bytes == 0;
    }

    private static RepositoryFile file(SessionFile file) {
        return new RepositoryFile(file.id(), file.name(), file.createdAt(), file.size(), file.recording(), null);
    }
}
