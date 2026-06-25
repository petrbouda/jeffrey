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

import cafe.jeffrey.hub.api.v1.InstanceStatus;
import cafe.jeffrey.hub.api.v1.RecordingStatus;
import cafe.jeffrey.hub.api.v1.SettingsLevel;
import cafe.jeffrey.hub.api.v1.WorkspaceStatus;
import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;

/**
 * Shared domain-to-proto conversions for the hub gRPC services. Centralizes the null-to-empty-string
 * coercion ({@link #orEmpty(String)}) and the enum mappings that were previously copy-pasted across
 * individual service classes (notably {@code recordingStatus}, which was duplicated verbatim).
 */
public abstract class ProtoMappers {

    public static String orEmpty(String value) {
        return value != null ? value : "";
    }

    public static RecordingStatus recordingStatus(
            cafe.jeffrey.shared.common.model.repository.RecordingStatus status) {
        if (status == null) {
            return RecordingStatus.RECORDING_STATUS_UNKNOWN;
        }
        return switch (status) {
            case ACTIVE -> RecordingStatus.RECORDING_STATUS_ACTIVE;
            case FINISHED -> RecordingStatus.RECORDING_STATUS_FINISHED;
            case UNKNOWN -> RecordingStatus.RECORDING_STATUS_UNKNOWN;
        };
    }

    public static WorkspaceStatus workspaceStatus(
            cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus status) {
        return switch (status) {
            case AVAILABLE -> WorkspaceStatus.WORKSPACE_STATUS_AVAILABLE;
            case UNAVAILABLE -> WorkspaceStatus.WORKSPACE_STATUS_UNAVAILABLE;
            case OFFLINE, UNKNOWN -> WorkspaceStatus.WORKSPACE_STATUS_INCOMPATIBLE;
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

    public static SettingsLevel settingsLevel(EffectiveProfilerSettings.SettingsLevel level) {
        return switch (level) {
            case PROJECT -> SettingsLevel.SETTINGS_LEVEL_PROJECT;
            case WORKSPACE -> SettingsLevel.SETTINGS_LEVEL_WORKSPACE;
            case GLOBAL -> SettingsLevel.SETTINGS_LEVEL_GLOBAL;
            case NONE -> SettingsLevel.SETTINGS_LEVEL_UNSPECIFIED;
        };
    }
}
