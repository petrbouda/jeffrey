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

package cafe.jeffrey.microscope.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ProjectInstanceInfo(
        String id,
        String projectId,
        String instanceName,
        ProjectInstanceStatus status,
        Instant startedAt,
        Instant finishedAt,
        Instant expiringAt,
        Instant expiredAt,
        int sessionCount,
        String activeSessionId,
        List<ProjectInstanceSessionInfo> sessions) {

    public ProjectInstanceInfo(
            String id,
            String projectId,
            String instanceName,
            ProjectInstanceStatus status,
            Instant startedAt,
            Instant finishedAt,
            Instant expiringAt,
            Instant expiredAt,
            int sessionCount,
            String activeSessionId) {
        this(id, projectId, instanceName, status, startedAt, finishedAt, expiringAt, expiredAt,
                sessionCount, activeSessionId, List.of());
    }

    public enum ProjectInstanceStatus {
        PENDING, ACTIVE, FINISHED, EXPIRED;

        /**
         * Returns the set of statuses that are valid predecessors for transitioning
         * to this status. Empty set means this is an initial state (set via insert only).
         */
        public Set<ProjectInstanceStatus> validFromStatuses() {
            return switch (this) {
                case PENDING -> Set.of();
                // EXPIRED is a valid predecessor: an expired instance that starts streaming
                // a new session is reactivated by the session-created consumer
                case ACTIVE -> Set.of(PENDING, FINISHED, EXPIRED);
                case FINISHED -> Set.of(ACTIVE);
                case EXPIRED -> Set.of(FINISHED);
            };
        }
    }
}
