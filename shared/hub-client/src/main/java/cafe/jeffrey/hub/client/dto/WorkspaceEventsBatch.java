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

package cafe.jeffrey.hub.client.dto;

import java.util.List;

/**
 * One delivery from a workspace event subscription.
 *
 * @param events     the events in this batch, ascending by id; empty on a heartbeat
 * @param lastOffset the highest event id the server has read, <b>before</b> type filtering. Clients
 *                   must resume from this rather than from the largest id they actually received,
 *                   or a filtered subscription would rescan the events it skipped on every reconnect.
 * @param caughtUp   true once the backlog is drained and the stream is tailing
 */
public record WorkspaceEventsBatch(
        List<WorkspaceEventResponse> events,
        long lastOffset,
        boolean caughtUp) {

    public WorkspaceEventsBatch {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
