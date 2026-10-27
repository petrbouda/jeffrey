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

package cafe.jeffrey.hub.stub.grpc;

import cafe.jeffrey.hub.api.v1.InstanceInfo;
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
        };
    }

    static ProjectInfo projectInfo(StubDataset.Project project) {
        ProjectInfo.Builder builder = ProjectInfo.newBuilder()
                .setId(project.id())
                .setName(project.name())
                .setCreatedAt(project.createdAt().toEpochMilli())
                .setWorkspaceId(project.workspaceId())
                .setStatus(recordingStatus(project.status()))
                .setSessionCount(project.sessionCount())
                .setSizeBytes(project.sizeBytes());
        return builder.build();
    }

    static InstanceInfo instanceInfo(StubDataset.Instance instance) {
        InstanceInfo.Builder builder = InstanceInfo.newBuilder()
                .setId(instance.id())
                .setStatus(instanceStatus(instance.status()))
                .setCreatedAt(instance.createdAt().toEpochMilli())
                .setSessionCount(instance.sessions().size());
        if (instance.finishedAt() != null) {
            builder.setFinishedAt(instance.finishedAt().toEpochMilli());
        }
        return builder.build();
    }

    /** A session with its files, as GetSession sends it. */
    static RecordingSession recordingSession(StubDataset.Session session) {
        RecordingSession.Builder builder = sessionBuilder(session);
        for (StubDataset.File file : session.files()) {
            builder.addFiles(repositoryFile(file));
        }
        return builder.build();
    }

    /** A session as a listing sends it: its file count and size, not its files. */
    static RecordingSession sessionHeader(StubDataset.Session session) {
        return sessionBuilder(session).build();
    }

    private static RecordingSession.Builder sessionBuilder(StubDataset.Session session) {
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
        return builder
                .setFileCount(session.files().size())
                .setSizeBytes(session.sizeBytes());
    }

    static RepositoryFile repositoryFile(StubDataset.File file) {
        return RepositoryFile.newBuilder()
                .setId(file.id())
                .setName(file.name())
                .setCreatedAt(file.createdAt().toEpochMilli())
                .setSize(file.size())
                .setIsRecording(file.kind().recording())
                .build();
    }
}
