/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.shared.common.model.repository;

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
