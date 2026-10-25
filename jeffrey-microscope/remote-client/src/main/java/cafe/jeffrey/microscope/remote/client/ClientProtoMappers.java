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

package cafe.jeffrey.microscope.remote.client;

import cafe.jeffrey.hub.api.v1.RecordingSession;
import cafe.jeffrey.hub.api.v1.RepositoryFile;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionResponse;
import cafe.jeffrey.microscope.remote.client.dto.RepositoryFileResponse;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;

import java.util.List;

/**
 * Shared proto-to-domain conversions for the hub gRPC clients — the enum mappings and the
 * session decoder every client that receives sessions uses — so no client class carries a copy.
 */
public abstract class ClientProtoMappers {

    public static String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    public static String orEmpty(String value) {
        return value != null ? value : "";
    }

    public static RecordingStatus recordingStatus(cafe.jeffrey.hub.api.v1.RecordingStatus status) {
        return switch (status) {
            case RECORDING_STATUS_ACTIVE -> RecordingStatus.ACTIVE;
            case RECORDING_STATUS_FINISHED -> RecordingStatus.FINISHED;
            default -> RecordingStatus.UNKNOWN;
        };
    }

    /**
     * Domain-to-proto status for a filter, where {@code null} means "any status" and maps to
     * {@code UNSPECIFIED}.
     */
    public static cafe.jeffrey.hub.api.v1.RecordingStatus recordingStatus(RecordingStatus status) {
        if (status == null) {
            return cafe.jeffrey.hub.api.v1.RecordingStatus.RECORDING_STATUS_UNSPECIFIED;
        }
        return switch (status) {
            case ACTIVE -> cafe.jeffrey.hub.api.v1.RecordingStatus.RECORDING_STATUS_ACTIVE;
            case FINISHED -> cafe.jeffrey.hub.api.v1.RecordingStatus.RECORDING_STATUS_FINISHED;
            case UNKNOWN -> cafe.jeffrey.hub.api.v1.RecordingStatus.RECORDING_STATUS_UNKNOWN;
        };
    }

    public static WorkspaceStatus workspaceStatus(cafe.jeffrey.hub.api.v1.WorkspaceStatus status) {
        return switch (status) {
            case WORKSPACE_STATUS_AVAILABLE -> WorkspaceStatus.AVAILABLE;
            case WORKSPACE_STATUS_UNAVAILABLE -> WorkspaceStatus.UNAVAILABLE;
            default -> WorkspaceStatus.UNKNOWN;
        };
    }

    /**
     * The hub always sends a status; one it did not set is refused here rather than turned into a
     * value no instance can have.
     */
    public static ProjectInstanceStatus instanceStatus(cafe.jeffrey.hub.api.v1.InstanceStatus status) {
        return switch (status) {
            case INSTANCE_STATUS_PENDING -> ProjectInstanceStatus.PENDING;
            case INSTANCE_STATUS_ACTIVE -> ProjectInstanceStatus.ACTIVE;
            case INSTANCE_STATUS_FINISHED -> ProjectInstanceStatus.FINISHED;
            case INSTANCE_STATUS_UNSPECIFIED, UNRECOGNIZED ->
                    throw new IllegalArgumentException("The hub sent an instance without a status: status=" + status);
        };
    }

    /**
     * A session as the hub sent it: from a listing with its {@code SessionSize} and no files, from
     * a single-session read with both.
     */
    public static RecordingSessionResponse session(RecordingSession proto) {
        List<RepositoryFileResponse> files = proto.getFilesList().stream()
                .map(ClientProtoMappers::file)
                .toList();

        return new RecordingSessionResponse(
                proto.getId(),
                nullIfEmpty(proto.getName()),
                proto.hasInstanceId() ? proto.getInstanceId() : null,
                proto.getCreatedAt(),
                proto.hasFinishedAt() ? proto.getFinishedAt() : null,
                recordingStatus(proto.getStatus()),
                proto.hasFinishedAt() ? proto.getFinishedAt() - proto.getCreatedAt() : null,
                files,
                proto.getRetained(),
                proto.getHeartbeatMissing(),
                proto.getFailed(),
                proto.getFileCount(),
                proto.getSizeBytes())
                // Here rather than at each call site. A file's status is a fact about its
                // session, so it can only be settled once the whole session is decoded — and
                // settling it inside the one method that decodes one means no caller can be
                // handed a session whose file statuses nobody filled in.
                .withResolvedFileStatuses();
    }

    /**
     * One file as the hub sent it. Its status is left FINISHED here and settled by
     * {@link RecordingSessionResponse#withResolvedFileStatuses()} before the session leaves
     * {@link #session}: the wire carries no status, and a file on its own cannot
     * answer for one.
     *
     * <p>Its type is classified from the name here: the wire carries none, because the hub
     * reads none of the files it serves and has nothing to say about a log or a heap dump
     * beyond that it is not a recording.
     */
    private static RepositoryFileResponse file(RepositoryFile proto) {
        return new RepositoryFileResponse(
                proto.getId(),
                proto.getName(),
                proto.getCreatedAt() != 0 ? proto.getCreatedAt() : null,
                proto.getSize(),
                RepositoryFiles.typeOf(proto.getName()),
                RecordingStatus.FINISHED,
                proto.getIsRecording());
    }
}
