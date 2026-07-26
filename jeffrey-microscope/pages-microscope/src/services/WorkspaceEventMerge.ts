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

import WorkspaceEvent from '@/services/api/model/WorkspaceEvent';

/**
 * Merges freshly streamed events into the list already on screen.
 *
 * Kept as a pure function so it can be unit-tested without mounting a component. Three things
 * matter here:
 *  - de-duplication by eventId, because the initial page load and the stream's catch-up phase
 *    legitimately overlap;
 *  - newest-first ordering, matching what the unary endpoint returns;
 *  - a hard cap, because a live stream on a long-lived tab would otherwise grow without bound.
 *
 * @param existing events currently displayed
 * @param incoming events just received
 * @param cap maximum events to retain; non-positive means unbounded
 */
export function mergeEvents(
  existing: WorkspaceEvent[],
  incoming: WorkspaceEvent[],
  cap: number
): WorkspaceEvent[] {
  if (incoming.length === 0) {
    return capped(existing, cap);
  }

  const byId = new Map<number, WorkspaceEvent>();
  for (const event of existing) {
    byId.set(event.eventId, event);
  }
  for (const event of incoming) {
    byId.set(event.eventId, event);
  }

  const merged = Array.from(byId.values());
  merged.sort((left, right) => {
    const byCreatedAt = right.createdAt - left.createdAt;
    if (byCreatedAt !== 0) {
      return byCreatedAt;
    }
    // Same millisecond: fall back to the queue offset so ordering stays stable.
    return right.eventId - left.eventId;
  });

  return capped(merged, cap);
}

/**
 * Highest event id in the list, or the given fallback when it is empty. Used to seed the
 * stream's resume offset from whatever the initial page load returned.
 */
export function highestEventId(events: WorkspaceEvent[], fallback: number = 0): number {
  return events.reduce((highest, event) => Math.max(highest, event.eventId), fallback);
}

function capped(events: WorkspaceEvent[], cap: number): WorkspaceEvent[] {
  if (cap > 0 && events.length > cap) {
    return events.slice(0, cap);
  }
  return events;
}
