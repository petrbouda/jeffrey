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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;

/**
 * Shared proto-to-domain conversions for the hub gRPC clients. Holds the empty-string-to-null
 * coercion ({@link #nullIfEmpty(String)}) and the enum mappings, so no individual client class
 * carries a copy of either.
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

    public static String instanceStatus(cafe.jeffrey.hub.api.v1.InstanceStatus status) {
        return switch (status) {
            case INSTANCE_STATUS_PENDING -> "PENDING";
            case INSTANCE_STATUS_ACTIVE -> "ACTIVE";
            case INSTANCE_STATUS_FINISHED -> "FINISHED";
            case INSTANCE_STATUS_EXPIRED -> "EXPIRED";
            default -> "UNKNOWN";
        };
    }
}
