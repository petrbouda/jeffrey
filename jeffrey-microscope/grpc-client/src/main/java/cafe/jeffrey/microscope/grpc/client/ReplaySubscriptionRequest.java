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

package cafe.jeffrey.microscope.grpc.client;

import java.util.Set;

/**
 * What a replay asks the Hub for: one session, the event types to read, an optional time window
 * and — for the scoped RPC — the workspace and project the session must belong to.
 *
 * <p>The invariants live here rather than in each caller, so a request that reaches
 * {@link EventStreamingClient} is one the Hub would not reject on sight: a blank session, an
 * inverted window or half a scope all fail with {@link IllegalArgumentException} at construction.</p>
 *
 * @param sessionId   the recording session to replay, never blank
 * @param eventTypes  JFR event type names to receive, never null and never containing a blank name;
 *                    an empty set is passed through, and it is the Hub that refuses it
 * @param startTime   optional inclusive start (epoch millis)
 * @param endTime     optional inclusive end (epoch millis), strictly after {@code startTime} when both are set
 * @param workspaceId workspace the session belongs to; set together with {@code projectId} or not at all
 * @param projectId   project the session belongs to; set together with {@code workspaceId} or not at all
 */
public record ReplaySubscriptionRequest(
        String sessionId,
        Set<String> eventTypes,
        Long startTime,
        Long endTime,
        String workspaceId,
        String projectId) {

    public ReplaySubscriptionRequest {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (eventTypes == null) {
            throw new IllegalArgumentException("eventTypes must not be null");
        }
        if (eventTypes.stream().anyMatch(type -> type == null || type.isBlank())) {
            throw new IllegalArgumentException("eventTypes must not contain a blank event type");
        }
        if (startTime != null && endTime != null && startTime >= endTime) {
            throw new IllegalArgumentException("startTime must be strictly before endTime");
        }
        if ((workspaceId == null) != (projectId == null)) {
            throw new IllegalArgumentException("workspaceId and projectId must be given together");
        }
        if (workspaceId != null && (workspaceId.isBlank() || projectId.isBlank())) {
            throw new IllegalArgumentException("workspaceId and projectId must not be blank");
        }
        eventTypes = Set.copyOf(eventTypes);
    }

    public ReplaySubscriptionRequest(String sessionId, Set<String> eventTypes, Long startTime, Long endTime) {
        this(sessionId, eventTypes, startTime, endTime, null, null);
    }

    /**
     * Whether the request names the workspace and project the session belongs to. A scoped request
     * goes over the scoped RPC, which resolves the session inside that project; an unscoped one goes
     * over the legacy RPC, which looks the session up by id alone.
     */
    public boolean scoped() {
        return workspaceId != null;
    }
}
