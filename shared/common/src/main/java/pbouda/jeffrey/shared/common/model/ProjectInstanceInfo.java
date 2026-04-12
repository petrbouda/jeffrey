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

package pbouda.jeffrey.shared.common.model;

import java.time.Instant;
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
        String activeSessionId) {

    public enum ProjectInstanceStatus {
        PENDING, ACTIVE, FINISHED, EXPIRED;

        /**
         * Returns the set of statuses that are valid predecessors for transitioning
         * to this status. Empty set means this is an initial state (set via insert only).
         */
        public Set<ProjectInstanceStatus> validFromStatuses() {
            return switch (this) {
                case PENDING -> Set.of();
                case ACTIVE -> Set.of(PENDING, FINISHED);
                case FINISHED -> Set.of(ACTIVE);
                case EXPIRED -> Set.of(FINISHED);
            };
        }
    }
}
