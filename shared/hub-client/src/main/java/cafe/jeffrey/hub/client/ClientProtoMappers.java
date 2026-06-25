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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.shared.common.model.EffectiveProfilerSettings.SettingsLevel;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;

/**
 * Shared proto-to-domain conversions for the hub gRPC clients. Centralizes the empty-string-to-null
 * coercion ({@link #nullIfEmpty(String)}) and the enum mappings that were previously copy-pasted
 * across individual client classes (notably {@code recordingStatus}, which was duplicated verbatim).
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

    public static WorkspaceStatus workspaceStatus(cafe.jeffrey.hub.api.v1.WorkspaceStatus status) {
        return switch (status) {
            case WORKSPACE_STATUS_AVAILABLE -> WorkspaceStatus.AVAILABLE;
            case WORKSPACE_STATUS_UNAVAILABLE -> WorkspaceStatus.UNAVAILABLE;
            default -> WorkspaceStatus.UNKNOWN;
        };
    }

    public static SettingsLevel settingsLevel(cafe.jeffrey.hub.api.v1.SettingsLevel level) {
        return switch (level) {
            case SETTINGS_LEVEL_PROJECT -> SettingsLevel.PROJECT;
            case SETTINGS_LEVEL_WORKSPACE -> SettingsLevel.WORKSPACE;
            case SETTINGS_LEVEL_GLOBAL -> SettingsLevel.GLOBAL;
            default -> SettingsLevel.NONE;
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
