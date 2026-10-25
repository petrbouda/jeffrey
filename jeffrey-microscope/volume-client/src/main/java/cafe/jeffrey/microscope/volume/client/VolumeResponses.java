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

import cafe.jeffrey.hub.client.RepositoryFiles;
import cafe.jeffrey.hub.client.dto.InstanceResponse;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.hub.client.dto.RemoteProjectResponse;
import cafe.jeffrey.hub.client.dto.RepositoryFileResponse;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.microscope.model.repository.SessionSize;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;
import cafe.jeffrey.shared.volume.layout.InstanceState;
import cafe.jeffrey.shared.volume.layout.InstanceSummary;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.SessionFile;
import cafe.jeffrey.shared.volume.layout.VolumeSession;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The volume's types onto Microscope's own, shaped exactly as {@code ClientProtoMappers} decodes
 * what a hub sends — so a page or a tool cannot tell which kind of source a session came from.
 * What a hub decides before it sends (a session's status, whether it failed, an instance's state)
 * is decided here the same way, from the same volume.
 */
final class VolumeResponses {

    /** Joins a session's instance and its own directory into its name, as the hub does. */
    private static final String NAME_SEPARATOR = "/";

    private static final Map<InstanceState, ProjectInstanceStatus> INSTANCE_STATUSES = Map.of(
            InstanceState.PENDING, ProjectInstanceStatus.PENDING,
            InstanceState.ACTIVE, ProjectInstanceStatus.ACTIVE,
            InstanceState.FINISHED, ProjectInstanceStatus.FINISHED);

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

    /** The session in Microscope's model without its files, enough for a filter to judge. */
    static RecordingSession unlisted(VolumeSession session) {
        return new RecordingSession(
                session.id(),
                name(session),
                session.instanceId(),
                session.createdAt(),
                session.lifecycle().finishedAt(),
                status(session),
                null,
                List.of(),
                session.retained(),
                session.lifecycle().heartbeatMissing(),
                false,
                new SessionSize(0, 0));
    }

    /** The session in Microscope's model with its files, for deciding which chunk is still open. */
    static RecordingSession withModelFiles(VolumeSession session, List<SessionFile> files) {
        return new RecordingSession(
                session.id(),
                name(session),
                session.instanceId(),
                session.createdAt(),
                session.lifecycle().finishedAt(),
                status(session),
                null,
                files.stream().map(VolumeResponses::modelFile).toList(),
                session.retained(),
                session.lifecycle().heartbeatMissing());
    }

    static RepositoryFile modelFile(SessionFile file) {
        return new RepositoryFile(file.id(), file.name(), file.createdAt(), file.size(), file.recording(), null);
    }

    static InstanceResponse instance(InstanceSummary summary) {
        Long createdAt = summary.instance().createdAt().toEpochMilli();
        Long finishedAt = summary.finishedAt().map(Instant::toEpochMilli).orElse(null);
        return new InstanceResponse(
                summary.instance().id(),
                // An instance is named by its id, as the hub names it
                summary.instance().id(),
                INSTANCE_STATUSES.get(summary.state()).name(),
                createdAt,
                finishedAt,
                summary.sessionCount(),
                null,
                finishedAt != null ? finishedAt - createdAt : null,
                List.of());
    }

    /**
     * A project with its status and session count read off its instances, each from its newest
     * session alone; its size is never measured here, as only a hub's retention pass does that.
     */
    static RemoteProjectResponse project(ProjectTree project, String workspaceId, List<InstanceSummary> instances) {
        int sessionCount = instances.stream().mapToInt(InstanceSummary::sessionCount).sum();
        return new RemoteProjectResponse(
                project.marker().projectId(),
                project.marker().projectName(),
                project.marker().createdAt() != 0 ? Instant.ofEpochMilli(project.marker().createdAt()) : null,
                workspaceId,
                projectStatus(instances, sessionCount),
                sessionCount,
                RepositoryStatistics.NOT_YET_MEASURED);
    }

    /**
     * @param name      the name its marker gives it, or its id for a directory without one
     * @param createdAt from its marker, or the directory's own creation time
     */
    static WorkspaceInfo workspace(String id, String name, Instant createdAt, int projectCount) {
        return new WorkspaceInfo(id, id, null, name, null, null, createdAt, WorkspaceStatus.AVAILABLE, projectCount);
    }

    /** UNKNOWN without a session, as a hub sends no status for one; ACTIVE while any instance records. */
    private static RecordingStatus projectStatus(List<InstanceSummary> instances, int sessionCount) {
        if (sessionCount == 0) {
            return RecordingStatus.UNKNOWN;
        }
        boolean recording = instances.stream().anyMatch(instance -> instance.state() == InstanceState.ACTIVE);
        return recording ? RecordingStatus.ACTIVE : RecordingStatus.FINISHED;
    }

    private static RecordingSessionResponse response(
            VolumeSession session, List<SessionFile> measured, List<RepositoryFileResponse> carried) {

        long createdAt = session.createdAt().toEpochMilli();
        Long finishedAt = session.lifecycle().isRecording() ? null : session.lifecycle().finishedAt().toEpochMilli();
        long bytes = measured.stream().mapToLong(SessionFile::size).sum();
        return new RecordingSessionResponse(
                session.id(),
                name(session),
                session.instanceId(),
                createdAt,
                finishedAt,
                status(session),
                finishedAt != null ? finishedAt - createdAt : null,
                carried,
                session.retained(),
                session.lifecycle().heartbeatMissing(),
                failed(session, bytes),
                measured.size(),
                bytes)
                .withResolvedFileStatuses();
    }

    /**
     * A session that finished without producing any data — a crash before the first chunk. One
     * finished because it never sent a heartbeat is empty too, but it did not crash: it is reported
     * as missing its heartbeat instead, never as failed. The hub's rule, decided here for a volume
     * read without one.
     */
    private static boolean failed(VolumeSession session, long bytes) {
        return !session.lifecycle().isRecording() && bytes == 0 && !session.lifecycle().heartbeatMissing();
    }

    private static RepositoryFileResponse file(SessionFile file) {
        return new RepositoryFileResponse(
                file.id(),
                file.name(),
                file.createdAt().toEpochMilli(),
                file.size(),
                RepositoryFiles.typeOf(file.name()),
                RecordingStatus.FINISHED,
                file.recording());
    }

    private static RecordingStatus status(VolumeSession session) {
        return session.lifecycle().isRecording() ? RecordingStatus.ACTIVE : RecordingStatus.FINISHED;
    }

    private static String name(VolumeSession session) {
        return session.instanceId() + NAME_SEPARATOR + session.id();
    }
}
