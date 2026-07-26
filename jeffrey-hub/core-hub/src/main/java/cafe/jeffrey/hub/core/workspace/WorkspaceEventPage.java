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

package cafe.jeffrey.hub.core.workspace;

import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;

import java.util.List;

/**
 * One delivery on a live subscription.
 *
 * @param events    the filtered events, ascending by offset; empty on a heartbeat
 * @param lastOffset the highest <b>unfiltered</b> offset read so far. It advances even when
 *                   the type filter dropped every event in the window, which is what lets a
 *                   filtered subscriber resume without rescanning events it will never want.
 * @param caughtUp  true once the backlog is drained and the subscription is tailing
 */
public record WorkspaceEventPage(
        List<WorkspaceEvent> events,
        long lastOffset,
        boolean caughtUp) {

    public WorkspaceEventPage {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static WorkspaceEventPage heartbeat(long lastOffset) {
        return new WorkspaceEventPage(List.of(), lastOffset, true);
    }
}
