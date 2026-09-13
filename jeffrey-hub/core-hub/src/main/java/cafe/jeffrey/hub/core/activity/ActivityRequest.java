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

package cafe.jeffrey.hub.core.activity;

import cafe.jeffrey.shared.common.activity.ActivityLimits;

import java.util.Set;

/**
 * One scan's scope, window and filter.
 *
 * <p>The window is half-open, {@code [startTime, endTime)}. This record validates its bounds;
 * {@link EventActivity#add} rejects events at the exclusive upper bound. The reader's
 * {@code StreamingWindow} is inclusive at both ends, so an event landing exactly on
 * {@code endTime} can reach the counter and must be rejected there.</p>
 */
public record ActivityRequest(
        String workspaceId,
        String projectId,
        String sessionId,
        long startTime,
        long endTime,
        long bucketMillis,
        Set<String> eventTypes) {

    public ActivityRequest {
        for (String id : new String[]{workspaceId, projectId, sessionId}) {
            if (id == null || id.isBlank() || id.length() > ActivityLimits.MAX_ID_LENGTH) {
                throw new IllegalArgumentException(
                        "Workspace, project and session IDs must contain 1–" + ActivityLimits.MAX_ID_LENGTH + " characters");
            }
        }
        long duration;
        try {
            duration = Math.subtractExact(endTime, startTime);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Time range is too large", e);
        }
        if (duration <= 0 || bucketMillis <= 0 || (duration - 1) / bucketMillis >= ActivityLimits.MAX_BUCKETS) {
            throw new IllegalArgumentException("Use startTime < endTime and a positive bucket size producing at most "
                    + ActivityLimits.MAX_BUCKETS + " buckets");
        }
        eventTypes = eventTypes == null ? Set.of() : Set.copyOf(eventTypes);
        if (eventTypes.size() > ActivityLimits.MAX_FILTER_TYPES
                || eventTypes.stream().anyMatch(type -> type.isBlank() || type.length() > ActivityLimits.MAX_TYPE_LENGTH)) {
            throw new IllegalArgumentException("Specify at most " + ActivityLimits.MAX_FILTER_TYPES
                    + " nonempty event types, each at most " + ActivityLimits.MAX_TYPE_LENGTH + " characters");
        }
    }

    int bucketCount() {
        return (int) ((endTime - startTime - 1) / bucketMillis + 1);
    }
}
