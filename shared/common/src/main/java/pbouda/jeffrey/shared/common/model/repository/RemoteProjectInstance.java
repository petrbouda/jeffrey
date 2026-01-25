/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.shared.common.model.repository;

/**
 * Represents a project instance stored in the remote filesystem.
 * An instance represents a deployed POD/container that creates sessions.
 *
 * @param instanceId unique identifier for the instance (typically HOSTNAME)
 * @param projectId the project this instance belongs to
 * @param workspaceId the workspace containing the project
 * @param createdAt timestamp when the instance was created (epoch millis)
 * @param relativeInstancePath relative path from project directory to instance directory
 */
public record RemoteProjectInstance(
        String instanceId,
        String projectId,
        String workspaceId,
        long createdAt,
        String relativeInstancePath) {
}
