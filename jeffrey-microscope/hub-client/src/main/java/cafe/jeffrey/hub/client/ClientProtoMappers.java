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

import cafe.jeffrey.microscope.model.config.ConfigEntry;
import cafe.jeffrey.microscope.model.config.ScopedConfig;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.config.ConfigType;

import java.time.Instant;

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

    // ========== Scoped configuration ==========

    public static ScopedConfig scopedConfig(cafe.jeffrey.hub.api.v1.ScopedConfig config) {
        return new ScopedConfig(
                configScope(config.getKey().getScope()),
                nullIfEmpty(config.getKey().getWorkspaceId()),
                nullIfEmpty(config.getKey().getProjectId()),
                config.getEntriesList().stream().map(ClientProtoMappers::configEntry).toList(),
                config.getDigest());
    }

    private static ConfigEntry configEntry(cafe.jeffrey.hub.api.v1.ConfigEntry entry) {
        return new ConfigEntry(
                configType(entry.getType()),
                entry.getValue(),
                Instant.ofEpochMilli(entry.getUpdatedAt()));
    }

    public static cafe.jeffrey.hub.api.v1.ConfigScope configScope(ConfigScope scope) {
        return switch (scope) {
            case GLOBAL -> cafe.jeffrey.hub.api.v1.ConfigScope.CONFIG_SCOPE_GLOBAL;
            case WORKSPACE -> cafe.jeffrey.hub.api.v1.ConfigScope.CONFIG_SCOPE_WORKSPACE;
            case PROJECT -> cafe.jeffrey.hub.api.v1.ConfigScope.CONFIG_SCOPE_PROJECT;
        };
    }

    public static ConfigScope configScope(cafe.jeffrey.hub.api.v1.ConfigScope scope) {
        return switch (scope) {
            case CONFIG_SCOPE_GLOBAL -> ConfigScope.GLOBAL;
            case CONFIG_SCOPE_WORKSPACE -> ConfigScope.WORKSPACE;
            case CONFIG_SCOPE_PROJECT -> ConfigScope.PROJECT;
            case CONFIG_SCOPE_UNSPECIFIED, UNRECOGNIZED ->
                    throw new IllegalArgumentException("The hub returned a configuration scope this build does not know");
        };
    }

    public static cafe.jeffrey.hub.api.v1.ConfigType configType(ConfigType type) {
        return switch (type) {
            case ASPROF_SETTINGS -> cafe.jeffrey.hub.api.v1.ConfigType.CONFIG_TYPE_ASPROF_SETTINGS;
        };
    }

    /**
     * A type this build does not know is an error rather than a default: the catalogue is a
     * security boundary, and silently reading an unknown value as the first member would show an
     * operator one setting while the hub holds another.
     */
    public static ConfigType configType(cafe.jeffrey.hub.api.v1.ConfigType type) {
        return switch (type) {
            case CONFIG_TYPE_ASPROF_SETTINGS -> ConfigType.ASPROF_SETTINGS;
            case CONFIG_TYPE_UNSPECIFIED, UNRECOGNIZED ->
                    throw new IllegalArgumentException("The hub returned a configuration type this build does not know");
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
