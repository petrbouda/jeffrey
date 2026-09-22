/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.hub.core.grpc;

import cafe.jeffrey.hub.api.v1.ConfigEntry;
import cafe.jeffrey.hub.api.v1.ConfigScope;
import cafe.jeffrey.hub.api.v1.ConfigScopeKey;
import cafe.jeffrey.hub.api.v1.ConfigType;
import cafe.jeffrey.hub.api.v1.InstanceInfo;
import cafe.jeffrey.hub.api.v1.InstanceSessionInfo;
import cafe.jeffrey.hub.api.v1.InstanceStats;
import cafe.jeffrey.hub.api.v1.InstanceStatus;
import cafe.jeffrey.hub.api.v1.ProjectInfo;
import cafe.jeffrey.hub.api.v1.RecordingSession;
import cafe.jeffrey.hub.api.v1.RecordingStatus;
import cafe.jeffrey.hub.api.v1.RepositoryFile;
import cafe.jeffrey.hub.api.v1.SessionFilter;
import cafe.jeffrey.hub.api.v1.ScopedConfig;
import cafe.jeffrey.hub.api.v1.WorkspaceInfo;
import cafe.jeffrey.hub.api.v1.WorkspaceStatus;
import cafe.jeffrey.hub.core.manager.project.ProjectManager.DetailedProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.config.ScopedConfigEntry;
import cafe.jeffrey.shared.common.model.repository.AppliedConfigLayer;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.hub.model.repository.RecordingSessionFilter;

import java.time.Instant;
import java.util.List;
import java.util.Set;

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
            case EXPIRED -> InstanceStatus.INSTANCE_STATUS_EXPIRED;
        };
    }

    // ========== Scoped configuration ==========

    public static ConfigScope configScope(cafe.jeffrey.shared.common.config.ConfigScope scope) {
        return switch (scope) {
            case GLOBAL -> ConfigScope.CONFIG_SCOPE_GLOBAL;
            case WORKSPACE -> ConfigScope.CONFIG_SCOPE_WORKSPACE;
            case PROJECT -> ConfigScope.CONFIG_SCOPE_PROJECT;
        };
    }

    public static ConfigType configType(cafe.jeffrey.shared.common.config.ConfigType type) {
        return switch (type) {
            case ASPROF_SETTINGS -> ConfigType.CONFIG_TYPE_ASPROF_SETTINGS;
        };
    }

    public static ScopedConfig scopedConfig(cafe.jeffrey.hub.model.config.ScopedConfig config) {
        ScopedConfig.Builder builder = ScopedConfig.newBuilder()
                .setKey(configScopeKey(config.key()))
                .setDigest(config.digest());
        for (ScopedConfigEntry entry : config.entries()) {
            builder.addEntries(ConfigEntry.newBuilder()
                    .setType(configType(entry.type()))
                    .setValue(entry.value())
                    .setUpdatedAt(entry.updatedAt().toEpochMilli())
                    .build());
        }
        return builder.build();
    }

    public static ConfigScopeKey configScopeKey(cafe.jeffrey.hub.model.config.ScopedConfigKey key) {
        return ConfigScopeKey.newBuilder()
                .setScope(configScope(key.scope()))
                .setWorkspaceId(orEmpty(key.workspaceId()))
                .setProjectId(orEmpty(key.projectId()))
                .build();
    }

    // ========== Workspaces and projects ==========

    public static WorkspaceInfo workspace(cafe.jeffrey.hub.model.workspace.WorkspaceInfo info) {
        return WorkspaceInfo.newBuilder()
                .setId(info.id())
                .setName(info.name())
                .setReferenceId(orEmpty(info.referenceId()))
                .setCreatedAt(info.createdAt().toEpochMilli())
                .setProjectCount(info.projectCount())
                .setStatus(workspaceStatus(info.status()))
                .build();
    }

    public static ProjectInfo project(DetailedProjectInfo detail) {
        cafe.jeffrey.hub.model.ProjectInfo info = detail.projectInfo();
        ProjectInfo.Builder builder = ProjectInfo.newBuilder()
                .setId(info.id())
                .setOriginId(orEmpty(info.originId()))
                .setName(info.name())
                .setLabel(orEmpty(info.label()))
                .setNamespace(orEmpty(info.namespace()))
                .setCreatedAt(info.createdAt().toEpochMilli())
                .setWorkspaceId(info.workspaceId())
                .setStatus(recordingStatus(detail.status()))
                .setSessionCount(detail.sessionCount());
        if (info.deletedAt() != null) {
            builder.setDeletedAt(info.deletedAt().toEpochMilli());
        }
        return builder.build();
    }

    // ========== Sessions and files ==========

    public static RecordingSession session(cafe.jeffrey.hub.model.repository.RecordingSession session) {
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
        session.files().forEach(file -> builder.addFiles(file(file)));
        return builder.build();
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

    public static InstanceStats instanceStats(cafe.jeffrey.hub.model.repository.InstanceStats stats) {
        return InstanceStats.newBuilder()
                .setFileCount(stats.fileCount())
                .setTotalSizeBytes(stats.totalSizeBytes())
                .build();
    }

    /**
     * @param failedSessionIds the sessions that finished without producing data — known only
     *                         from the files on the volume, which is why the caller passes them
     */
    public static InstanceInfo instance(
            ProjectInstanceInfo info, List<ProjectInstanceSessionInfo> sessions, Set<String> failedSessionIds) {

        InstanceInfo.Builder builder = InstanceInfo.newBuilder()
                .setId(info.id())
                .setInstanceName(orEmpty(info.instanceName()))
                .setStatus(instanceStatus(info.status()))
                .setCreatedAt(info.startedAt().toEpochMilli())
                .setSessionCount(info.sessionCount());
        if (info.finishedAt() != null) {
            builder.setFinishedAt(info.finishedAt().toEpochMilli());
        }
        if (info.expiringAt() != null) {
            builder.setExpiringAt(info.expiringAt().toEpochMilli());
        }
        if (info.expiredAt() != null) {
            builder.setExpiredAt(info.expiredAt().toEpochMilli());
        }
        if (info.activeSessionId() != null) {
            builder.setActiveSessionId(info.activeSessionId());
        }
        for (ProjectInstanceSessionInfo session : sessions) {
            builder.addSessions(instanceSession(session, failedSessionIds));
        }
        return builder.build();
    }

    public static InstanceSessionInfo instanceSession(ProjectInstanceSessionInfo info, Set<String> failedSessionIds) {
        InstanceSessionInfo.Builder builder = InstanceSessionInfo.newBuilder()
                .setId(info.sessionId())
                .setRepositoryId(orEmpty(info.repositoryId()))
                .setCreatedAt(info.createdAt().toEpochMilli())
                .setIsActive(info.finishedAt() == null)
                .setFailed(failedSessionIds.contains(info.sessionId()));
        if (info.finishedAt() != null) {
            builder.setFinishedAt(info.finishedAt().toEpochMilli());
        }
        if (info.profilerCommandSource() != null) {
            builder.setProfilerCommandSource(info.profilerCommandSource().name());
        }
        if (info.profilerCommand() != null) {
            builder.setProfilerCommand(info.profilerCommand());
        }
        for (AppliedConfigLayer layer : info.configLayers()) {
            builder.addConfigLayers(cafe.jeffrey.hub.api.v1.AppliedConfigLayer.newBuilder()
                    .setScope(configScope(layer.scope()))
                    .setDigest(layer.digest())
                    .build());
        }
        return builder.build();
    }
}
