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

package cafe.jeffrey.hub.core.grpc;

import cafe.jeffrey.hub.api.v1.InstanceInfo;
import cafe.jeffrey.hub.api.v1.InstanceStatus;
import cafe.jeffrey.hub.api.v1.ProjectInfo;
import cafe.jeffrey.hub.api.v1.RecordingSession;
import cafe.jeffrey.hub.api.v1.RecordingStatus;
import cafe.jeffrey.hub.api.v1.RepositoryFile;
import cafe.jeffrey.hub.api.v1.SessionFilter;
import cafe.jeffrey.hub.api.v1.WorkspaceInfo;
import cafe.jeffrey.hub.api.v1.WorkspaceStatus;
import cafe.jeffrey.hub.core.manager.project.ProjectManager.DetailedProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.repository.RecordingSessionFilter;

import java.time.Instant;
import java.util.OptionalLong;

/**
 * Every domain-to-proto conversion of the hub's gRPC services, in one place, so that no
 * service carries a mapper of its own and the two spellings of a status live side by side.
 *
 * <p>The proto types are imported and the hub's own are written out in full where the names
 * collide ({@code RecordingSession}, {@code WorkspaceInfo}, ...): the proto side is built here
 * with its builders on every line, the domain side only names a parameter. This is the one
 * file allowed the qualified form — it is what keeps the five services free of it.</p>
 */
public final class ProtoMappers {

    private ProtoMappers() {
    }

    public static String orEmpty(String value) {
        return value != null ? value : "";
    }

    // ========== Enums ==========

    public static RecordingStatus recordingStatus(cafe.jeffrey.hub.model.repository.RecordingStatus status) {
        if (status == null) {
            return RecordingStatus.RECORDING_STATUS_UNKNOWN;
        }
        return switch (status) {
            case ACTIVE -> RecordingStatus.RECORDING_STATUS_ACTIVE;
            case FINISHED -> RecordingStatus.RECORDING_STATUS_FINISHED;
            case UNKNOWN -> RecordingStatus.RECORDING_STATUS_UNKNOWN;
        };
    }

    /**
     * Proto-to-domain conversion of a session filter. An unset bound stays open, an
     * {@code UNSPECIFIED} status matches every status and a zero limit means no cap, so a
     * default-instance filter converts to {@link RecordingSessionFilter#ALL}. An empty window
     * surfaces as {@link IllegalArgumentException}, which the unary envelope reports as
     * {@code INVALID_ARGUMENT}.
     */
    public static RecordingSessionFilter sessionFilter(SessionFilter filter) {
        Instant activeFrom = filter.hasActiveFrom() ? Instant.ofEpochMilli(filter.getActiveFrom()) : null;
        Instant activeTo = filter.hasActiveTo() ? Instant.ofEpochMilli(filter.getActiveTo()) : null;
        return new RecordingSessionFilter(activeFrom, activeTo, statusFilter(filter.getStatus()), filter.getLimit());
    }

    private static cafe.jeffrey.hub.model.repository.RecordingStatus statusFilter(RecordingStatus status) {
        return switch (status) {
            case RECORDING_STATUS_ACTIVE -> cafe.jeffrey.hub.model.repository.RecordingStatus.ACTIVE;
            case RECORDING_STATUS_FINISHED -> cafe.jeffrey.hub.model.repository.RecordingStatus.FINISHED;
            case RECORDING_STATUS_UNKNOWN -> cafe.jeffrey.hub.model.repository.RecordingStatus.UNKNOWN;
            case RECORDING_STATUS_UNSPECIFIED, UNRECOGNIZED -> null;
        };
    }

    public static WorkspaceStatus workspaceStatus(cafe.jeffrey.hub.model.workspace.WorkspaceStatus status) {
        return switch (status) {
            case AVAILABLE -> WorkspaceStatus.WORKSPACE_STATUS_AVAILABLE;
            case UNAVAILABLE -> WorkspaceStatus.WORKSPACE_STATUS_UNAVAILABLE;
            case UNKNOWN -> WorkspaceStatus.WORKSPACE_STATUS_UNSPECIFIED;
        };
    }

    public static InstanceStatus instanceStatus(ProjectInstanceInfo.ProjectInstanceStatus status) {
        return switch (status) {
            case PENDING -> InstanceStatus.INSTANCE_STATUS_PENDING;
            case ACTIVE -> InstanceStatus.INSTANCE_STATUS_ACTIVE;
            case FINISHED -> InstanceStatus.INSTANCE_STATUS_FINISHED;
        };
    }


    // ========== Workspaces and projects ==========

    public static WorkspaceInfo workspace(cafe.jeffrey.hub.model.workspace.WorkspaceInfo info) {
        return WorkspaceInfo.newBuilder()
                .setId(info.id())
                .setName(info.name())
                .setCreatedAt(info.createdAt().toEpochMilli())
                .setProjectCount(info.projectCount())
                .setStatus(workspaceStatus(info.status()))
                .build();
    }

    /**
     * @param sizeBytes what the last retention pass measured for the project, empty before the first
     */
    public static ProjectInfo project(DetailedProjectInfo detail, OptionalLong sizeBytes) {
        cafe.jeffrey.hub.model.ProjectInfo info = detail.projectInfo();
        ProjectInfo.Builder builder = ProjectInfo.newBuilder()
                .setId(info.id())
                .setName(info.name())
                .setCreatedAt(info.createdAt().toEpochMilli())
                .setWorkspaceId(info.workspaceId())
                .setStatus(recordingStatus(detail.status()))
                .setSessionCount(detail.sessionCount());
        sizeBytes.ifPresent(builder::setSizeBytes);
        return builder.build();
    }

    // ========== Sessions and files ==========

    /**
     * A session with its files, which only a single-session read sends.
     */
    public static RecordingSession session(cafe.jeffrey.hub.model.repository.RecordingSession session) {
        RecordingSession.Builder builder = sessionBuilder(session);
        session.files().forEach(file -> builder.addFiles(file(file)));
        return builder.build();
    }

    /**
     * A session as a listing sends it: its file count and size, not its files. The session must
     * still have been loaded with its files, since both figures — and whether it failed — are
     * read off them.
     */
    public static RecordingSession sessionHeader(cafe.jeffrey.hub.model.repository.RecordingSession session) {
        return sessionBuilder(session).build();
    }

    private static RecordingSession.Builder sessionBuilder(cafe.jeffrey.hub.model.repository.RecordingSession session) {
        RecordingSession.Builder builder = RecordingSession.newBuilder()
                .setId(session.id())
                .setName(orEmpty(session.name()))
                .setCreatedAt(session.createdAt().toEpochMilli())
                .setStatus(recordingStatus(session.status()))
                .setRetained(session.retained());
        if (session.instanceId() != null) {
            builder.setInstanceId(session.instanceId());
        }
        if (session.finishedAt() != null) {
            builder.setFinishedAt(session.finishedAt().toEpochMilli());
        }
        builder.setHeartbeatMissing(session.heartbeatMissing());
        builder.setFailed(isFailed(session));
        builder.setFileCount(session.files().size());
        builder.setSizeBytes(session.totalSizeBytes());
        return builder;
    }

    /**
     * A failed session finished without producing any data, which only the files on the volume
     * can say — so the session must have been loaded with its files. A session the hub finished
     * because it never sent a heartbeat is empty too, but it did not crash: it is reported as
     * missing its heartbeat instead, never as failed.
     */
    private static boolean isFailed(cafe.jeffrey.hub.model.repository.RecordingSession session) {
        return session.isFailedEmpty() && !session.heartbeatMissing();
    }

    public static RepositoryFile file(cafe.jeffrey.hub.model.repository.RepositoryFile file) {
        return RepositoryFile.newBuilder()
                .setId(file.id())
                .setName(file.name())
                .setCreatedAt(file.createdAt().toEpochMilli())
                .setSize(file.size())
                .setIsRecording(file.isRecordingFile())
                .build();
    }

    // ========== Instances ==========

    public static InstanceInfo instance(ProjectInstanceInfo info) {
        InstanceInfo.Builder builder = InstanceInfo.newBuilder()
                .setId(info.id())
                .setStatus(instanceStatus(info.status()))
                .setCreatedAt(info.startedAt().toEpochMilli())
                .setSessionCount(info.sessionCount());
        if (info.finishedAt() != null) {
            builder.setFinishedAt(info.finishedAt().toEpochMilli());
        }
        return builder.build();
    }
}
