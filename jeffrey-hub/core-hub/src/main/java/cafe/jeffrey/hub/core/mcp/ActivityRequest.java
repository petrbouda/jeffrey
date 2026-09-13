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

package cafe.jeffrey.hub.core.mcp;

import java.util.Set;

/** Half-open epoch-millisecond window; buckets are anchored at its start. */
public record ActivityRequest(String workspaceId, String projectId, String sessionId,
                              long startTime, long endTime, long bucketMillis, Set<String> eventTypes) {
    private static final int MAX_ID_LENGTH = 512;
    private static final int MAX_BUCKETS = 288;
    private static final int MAX_FILTER_TYPES = 16;
    private static final int MAX_TYPE_LENGTH = 256;

    public ActivityRequest {
        for (String id : new String[]{workspaceId, projectId, sessionId}) {
            if (id == null || id.isBlank() || id.length() > MAX_ID_LENGTH) {
                throw new IllegalArgumentException("Workspace, project and session IDs must contain 1–512 characters");
            }
        }
        long duration;
        try {
            duration = Math.subtractExact(endTime, startTime);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Time range is too large", e);
        }
        if (duration <= 0 || bucketMillis <= 0 || (duration - 1) / bucketMillis >= MAX_BUCKETS) {
            throw new IllegalArgumentException("Use startTime < endTime and a positive bucket size producing at most 288 buckets");
        }
        eventTypes = eventTypes == null ? Set.of() : Set.copyOf(eventTypes);
        if (eventTypes.size() > MAX_FILTER_TYPES || eventTypes.stream().anyMatch(type -> type.isBlank() || type.length() > MAX_TYPE_LENGTH)) {
            throw new IllegalArgumentException("Specify at most 16 nonempty event types, each at most 256 characters");
        }
    }

    int bucketCount() {
        return (int) ((endTime - startTime - 1) / bucketMillis + 1);
    }
}
