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

package cafe.jeffrey.hub.persistence.api;

import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;

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
     * Find all project instances with the given status.
     *
     * @param status the status to filter by
     * @return list of matching project instances
     */
    List<ProjectInstanceInfo> findByStatus(ProjectInstanceStatus status);

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
     * Update instance status.
     *
     * @param instanceId the instance ID
     * @param status     the new status
     */
    void updateStatus(String instanceId, ProjectInstanceStatus status);

    /**
     * Update instance status and set finished_at timestamp.
     *
     * @param instanceId the instance ID
     * @param status     the new status
     * @param finishedAt when the instance finished
     */
    void updateStatusAndFinishedAt(String instanceId, ProjectInstanceStatus status, Instant finishedAt);

    /**
     * Update instance status and set expired_at timestamp.
     *
     * @param instanceId the instance ID
     * @param status     the new status
     * @param expiredAt  when the instance expired
     */
    void updateStatusAndExpiredAt(String instanceId, ProjectInstanceStatus status, Instant expiredAt);

    /**
     * Set the expiring_at timestamp on an instance, indicating data truncation has started.
     *
     * @param instanceId the instance ID
     * @param expiringAt when expiration started
     */
    void setExpiringAt(String instanceId, Instant expiringAt);

    /**
     * Delete an instance by ID.
     *
     * @param instanceId the instance ID to delete
     */
    void delete(String instanceId);

}
