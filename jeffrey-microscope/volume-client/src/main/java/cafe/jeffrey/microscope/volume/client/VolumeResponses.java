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

import cafe.jeffrey.microscope.remote.client.RepositoryFiles;
import cafe.jeffrey.microscope.remote.client.dto.InstanceResponse;
import cafe.jeffrey.microscope.remote.client.dto.ProjectActivity;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionResponse;
import cafe.jeffrey.microscope.remote.client.dto.RemoteProjectResponse;
import cafe.jeffrey.microscope.remote.client.dto.RepositoryFileResponse;
import cafe.jeffrey.microscope.model.repository.RecordedSpan;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.microscope.model.repository.SessionSize;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;
import cafe.jeffrey.shared.volume.layout.InstanceSummary;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.SessionFile;
import cafe.jeffrey.shared.volume.layout.VolumeSession;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The volume's types onto Microscope's own. What the hub's jobs decide from the same volume
 * (whether a session failed, which recording may still be on its way) is decided here by the same
 * rules, so Microscope and the hub never disagree about a session.
 */
final class VolumeResponses {

    private VolumeResponses() {
    }

    /** A session as a listing carries it: its file count and size, none of its files. */
    static RecordingSessionResponse header(VolumeSession session, List<SessionFile> files) {
        return response(session, files, List.of());
    }

    /** A session read on its own, with every file. */
    static RecordingSessionResponse withFiles(VolumeSession session, List<SessionFile> files) {
        return response(session, files, files.stream().map(VolumeResponses::file).toList());
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

    static InstanceResponse instance(InstanceSummary summary, ProjectTree project) {
        return InstanceResponse.summary(
                summary.instance().id(),
                summary.instance().createdAt().toEpochMilli(),
                lastDataAt(summary, project).map(Instant::toEpochMilli).orElse(null),
                summary.sessionCount());
    }

    /** A project as its marker describes it; its size is never measured here, only by the hub's retention pass. */
    static RemoteProjectResponse project(ProjectTree project, String workspaceId) {
        return new RemoteProjectResponse(
                project.marker().projectId(),
                project.marker().projectName(),
                project.marker().createdAt() != 0 ? Instant.ofEpochMilli(project.marker().createdAt()) : null,
                workspaceId,
                RepositoryStatistics.NOT_YET_MEASURED);
    }

    /** What a project's instances say of it, each read off its newest session alone. */
    static ProjectActivity activity(List<InstanceSummary> instances, ProjectTree project) {
        int sessionCount = instances.stream().mapToInt(InstanceSummary::sessionCount).sum();
        Instant lastDataAt = instances.stream()
                .map(instance -> lastDataAt(instance, project))
                .flatMap(Optional::stream)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new ProjectActivity(lastDataAt, sessionCount);
    }

    /**
     * @param name      the name its marker gives it, or its id for a directory without one
     * @param createdAt from its marker, or the directory's own creation time
     */
    static WorkspaceInfo workspace(String id, String name, Instant createdAt, int projectCount) {
        return new WorkspaceInfo(id, id, null, name, null, null, createdAt, WorkspaceStatus.AVAILABLE, projectCount);
    }

    /** When the data of the instance's newest session ends, off that session's listing. */
    private static Optional<Instant> lastDataAt(InstanceSummary summary, ProjectTree project) {
        return summary.newest().flatMap(newest -> RecordedSpan.end(project.recordingStarts(newest)));
    }

    private static RecordingSessionResponse response(
            VolumeSession session, List<SessionFile> measured, List<RepositoryFileResponse> carried) {

        long createdAt = session.createdAt().toEpochMilli();
        Long lastDataAt = RecordedSpan.end(recordingStarts(measured)).map(Instant::toEpochMilli).orElse(null);
        long bytes = measured.stream().mapToLong(SessionFile::size).sum();
        return new RecordingSessionResponse(
                session.id(),
                session.name(),
                session.instanceId(),
                createdAt,
                lastDataAt,
                session.newest(),
                lastDataAt != null ? lastDataAt - createdAt : null,
                carried,
                failed(session, bytes),
                measured.size(),
                bytes)
                .withResolvedFiles();
    }

    private static List<Instant> recordingStarts(List<SessionFile> files) {
        return files.stream()
                .filter(SessionFile::recording)
                .map(SessionFile::createdAt)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * A session a newer one ended before it produced any data — a crash before the first chunk.
     * The newest session is never failed: it may simply not have written anything yet. The rule
     * the hub's retention applies too.
     */
    private static boolean failed(VolumeSession session, long bytes) {
        return !session.newest() && bytes == 0;
    }

    private static RepositoryFileResponse file(SessionFile file) {
        return new RepositoryFileResponse(
                file.id(),
                file.name(),
                file.createdAt().toEpochMilli(),
                file.size(),
                RepositoryFiles.typeOf(file.name()),
                false,
                file.recording());
    }
}
