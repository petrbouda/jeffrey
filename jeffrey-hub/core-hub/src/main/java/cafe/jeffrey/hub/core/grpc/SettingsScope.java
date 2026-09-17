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

/**
 * Where a profiler-settings row applies: the whole hub (no ids), one workspace, or one project
 * of a workspace. Built from the two wire fields, where an empty string means "not given", and
 * refuses a project with no workspace, since the row is keyed on both and a project row without
 * its workspace would be found by no reader.
 */
public record SettingsScope(String workspaceId, String projectId) {

    public SettingsScope {
        if (projectId != null && workspaceId == null) {
            throw new IllegalArgumentException("Workspace ID is required when Project ID is provided");
        }
    }

    public static SettingsScope of(String workspaceId, String projectId) {
        return new SettingsScope(orNull(workspaceId), orNull(projectId));
    }

    public boolean isGlobal() {
        return workspaceId == null;
    }

    public boolean isProject() {
        return projectId != null;
    }

    private static String orNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
