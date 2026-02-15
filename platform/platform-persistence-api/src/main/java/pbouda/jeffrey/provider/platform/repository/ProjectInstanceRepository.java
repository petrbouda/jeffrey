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

package pbouda.jeffrey.provider.platform.repository;

import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing project instances within a project.
 * Project instances represent running containers/pods that connect to the platform.
 */
public interface ProjectInstanceRepository {

    /**
     * Find all project instances for the project.
     *
     * @return list of all project instances
     */
    List<ProjectInstanceInfo> findAll();

    /**
     * Find a specific project instance by ID.
     *
     * @param instanceId the instance ID
     * @return project instance info if found
     */
    Optional<ProjectInstanceInfo> find(String instanceId);

    /**
     * Find all sessions for a specific project instance.
     * Sessions are project instance sessions that have an instance_id set.
     *
     * @param instanceId the instance ID
     * @return list of sessions for the project instance
     */
    List<ProjectInstanceSessionInfo> findSessions(String instanceId);

    /**
     * Insert a new project instance.
     *
     * @param instance the project instance to insert
     */
    void insert(ProjectInstanceInfo instance);

    /**
     * Mark a project instance as finished with a timestamp.
     *
     * @param instanceId the instance ID
     * @param finishedAt the finished timestamp
     */
    void markFinished(String instanceId, Instant finishedAt);

    /**
     * Re-activate a finished project instance by setting its status back to ACTIVE
     * and clearing the finished timestamp. This is the inverse of {@link #markFinished}.
     *
     * @param instanceId the instance ID
     */
    void reactivate(String instanceId);

    /**
     * Update the status of a project instance.
     *
     * @param instanceId the instance ID
     * @param status the new status
     */
    void updateStatus(String instanceId, ProjectInstanceStatus status);
}
