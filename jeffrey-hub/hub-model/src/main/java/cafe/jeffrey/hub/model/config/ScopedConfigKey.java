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


package cafe.jeffrey.hub.model.config;

import cafe.jeffrey.shared.common.config.ConfigScope;

/**
 * Which scope a stored configuration value belongs to.
 *
 * <p>Which ids a scope takes follows from the scope itself, so the record refuses any other
 * combination rather than leaving each reader to decide what an odd one means: a project id
 * without a workspace once meant "global" to one reader and "project" to another, and a single
 * project's settings reached every agent as a result.</p>
 */
public record ScopedConfigKey(ConfigScope scope, String workspaceId, String projectId) {

    public ScopedConfigKey {
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        switch (scope) {
            case GLOBAL -> require(workspaceId == null && projectId == null,
                    "a global scope takes no workspace and no project");
            case WORKSPACE -> require(workspaceId != null && projectId == null,
                    "a workspace scope takes a workspace and no project");
            case PROJECT -> require(workspaceId != null && projectId != null,
                    "a project scope takes both a workspace and a project");
        }
    }

    public static ScopedConfigKey global() {
        return new ScopedConfigKey(ConfigScope.GLOBAL, null, null);
    }

    public static ScopedConfigKey workspace(String workspaceId) {
        return new ScopedConfigKey(ConfigScope.WORKSPACE, workspaceId, null);
    }

    public static ScopedConfigKey project(String workspaceId, String projectId) {
        return new ScopedConfigKey(ConfigScope.PROJECT, workspaceId, projectId);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
