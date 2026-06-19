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

package cafe.jeffrey.hub.stub.grpc;

import cafe.jeffrey.hub.api.v1.InstanceInfo;
import cafe.jeffrey.hub.api.v1.InstanceSessionInfo;
import cafe.jeffrey.hub.api.v1.InstanceStatus;
import cafe.jeffrey.hub.api.v1.ProjectInfo;
import cafe.jeffrey.hub.api.v1.RecordingSession;
import cafe.jeffrey.hub.api.v1.RecordingStatus;
import cafe.jeffrey.hub.api.v1.RepositoryFile;
import cafe.jeffrey.hub.stub.data.StubDataset;

/** Maps the framework-free {@link StubDataset} records onto the v1 protobuf messages. */
final class StubProtoMappers {

    private StubProtoMappers() {
    }

    static RecordingStatus recordingStatus(StubDataset.RecState state) {
        return switch (state) {
            case ACTIVE -> RecordingStatus.RECORDING_STATUS_ACTIVE;
            case FINISHED -> RecordingStatus.RECORDING_STATUS_FINISHED;
            case UNKNOWN -> RecordingStatus.RECORDING_STATUS_UNKNOWN;
        };
    }

    static InstanceStatus instanceStatus(StubDataset.InstState state) {
        return switch (state) {
            case PENDING -> InstanceStatus.INSTANCE_STATUS_PENDING;
            case ACTIVE -> InstanceStatus.INSTANCE_STATUS_ACTIVE;
            case FINISHED -> InstanceStatus.INSTANCE_STATUS_FINISHED;
            case EXPIRED -> InstanceStatus.INSTANCE_STATUS_EXPIRED;
        };
    }

    static ProjectInfo projectInfo(StubDataset.Project project) {
        ProjectInfo.Builder builder = ProjectInfo.newBuilder()
                .setId(project.id())
                .setOriginId(project.originId())
                .setName(project.name())
                .setCreatedAt(project.createdAt().toEpochMilli())
                .setWorkspaceId(project.workspaceId())
                .setStatus(recordingStatus(project.status()))
                .setSessionCount(project.sessionCount());
        if (project.label() != null) {
            builder.setLabel(project.label());
        }
        if (project.namespace() != null) {
            builder.setNamespace(project.namespace());
        }
        if (project.deletedAt() != null) {
            builder.setDeletedAt(project.deletedAt().toEpochMilli());
        }
        return builder.build();
    }

    static InstanceInfo instanceInfo(StubDataset.Instance instance, boolean includeSessions) {
        InstanceInfo.Builder builder = InstanceInfo.newBuilder()
                .setId(instance.id())
                .setInstanceName(instance.name())
                .setStatus(instanceStatus(instance.status()))
                .setCreatedAt(instance.createdAt().toEpochMilli())
                .setSessionCount(instance.sessions().size());
        if (instance.finishedAt() != null) {
            builder.setFinishedAt(instance.finishedAt().toEpochMilli());
        }
        if (instance.expiringAt() != null) {
            builder.setExpiringAt(instance.expiringAt().toEpochMilli());
        }
        if (instance.expiredAt() != null) {
            builder.setExpiredAt(instance.expiredAt().toEpochMilli());
        }
        if (instance.activeSessionId() != null) {
            builder.setActiveSessionId(instance.activeSessionId());
        }
        if (includeSessions) {
            for (StubDataset.Session session : instance.sessions()) {
                builder.addSessions(instanceSessionInfo(session));
            }
        }
        return builder.build();
    }

    static InstanceSessionInfo instanceSessionInfo(StubDataset.Session session) {
        InstanceSessionInfo.Builder builder = InstanceSessionInfo.newBuilder()
                .setId(session.id())
                .setRepositoryId(session.repositoryId())
                .setCreatedAt(session.createdAt().toEpochMilli())
                .setIsActive(session.active());
        if (session.finishedAt() != null) {
            builder.setFinishedAt(session.finishedAt().toEpochMilli());
        }
        return builder.build();
    }

    static RecordingSession recordingSession(StubDataset.Session session) {
        RecordingSession.Builder builder = RecordingSession.newBuilder()
                .setId(session.id())
                .setName(session.name())
                .setCreatedAt(session.createdAt().toEpochMilli())
                .setStatus(recordingStatus(session.status()));
        if (session.instanceId() != null) {
            builder.setInstanceId(session.instanceId());
        }
        if (session.finishedAt() != null) {
            builder.setFinishedAt(session.finishedAt().toEpochMilli());
        }
        for (StubDataset.File file : session.files()) {
            builder.addFiles(repositoryFile(file));
        }
        return builder.build();
    }

    static RepositoryFile repositoryFile(StubDataset.File file) {
        return RepositoryFile.newBuilder()
                .setId(file.id())
                .setName(file.name())
                .setCreatedAt(file.createdAt().toEpochMilli())
                .setSize(file.size())
                .setFileType(file.kind().fileType())
                .setStatus(recordingStatus(file.status()))
                .setIsRecording(file.kind().recording())
                .build();
    }
}
