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

import { describe, expect, it } from 'vitest';
import { highestEventId, mergeEvents } from '@/services/WorkspaceEventMerge';
import WorkspaceEvent from '@/services/api/model/WorkspaceEvent';
import WorkspaceEventType from '@/services/api/model/WorkspaceEventType';

function event(eventId: number, createdAt: number = eventId * 1000): WorkspaceEvent {
  return {
    eventId,
    originEventId: `origin-${eventId}`,
    projectId: 'proj-1',
    workspaceRefId: 'ws-1',
    eventType: WorkspaceEventType.PROJECT_CREATED,
    content: '{}',
    originCreatedAt: createdAt,
    createdAt,
    createdBy: 'system'
  };
}

describe('mergeEvents', () => {
  it('returns newest first', () => {
    const merged = mergeEvents([event(1)], [event(3), event(2)], 0);

    expect(merged.map(e => e.eventId)).toEqual([3, 2, 1]);
  });

  it('de-duplicates by eventId', () => {
    // The initial page load and the stream's catch-up phase legitimately overlap.
    const merged = mergeEvents([event(2), event(1)], [event(2), event(3)], 0);

    expect(merged.map(e => e.eventId)).toEqual([3, 2, 1]);
  });

  it('prefers the incoming copy of a duplicate', () => {
    const stale = event(1);
    const fresh = { ...event(1), createdBy: 'updated' };

    const merged = mergeEvents([stale], [fresh], 0);

    expect(merged).toHaveLength(1);
    expect(merged[0].createdBy).toBe('updated');
  });

  it('breaks createdAt ties by eventId so ordering stays stable', () => {
    const merged = mergeEvents([], [event(1, 5000), event(2, 5000), event(3, 5000)], 0);

    expect(merged.map(e => e.eventId)).toEqual([3, 2, 1]);
  });

  it('caps the retained list', () => {
    // Without this a live stream on a long-lived tab grows without bound.
    const merged = mergeEvents([event(1), event(2)], [event(3), event(4)], 3);

    expect(merged.map(e => e.eventId)).toEqual([4, 3, 2]);
  });

  it('treats a non-positive cap as unbounded', () => {
    const merged = mergeEvents([event(1)], [event(2), event(3)], 0);

    expect(merged).toHaveLength(3);
  });

  it('still applies the cap when nothing new arrives', () => {
    const merged = mergeEvents([event(3), event(2), event(1)], [], 2);

    expect(merged.map(e => e.eventId)).toEqual([3, 2]);
  });

  it('returns existing untouched when incoming is empty and cap allows', () => {
    const existing = [event(2), event(1)];

    expect(mergeEvents(existing, [], 0)).toBe(existing);
  });
});

describe('highestEventId', () => {
  it('finds the maximum id', () => {
    expect(highestEventId([event(3), event(7), event(5)])).toBe(7);
  });

  it('falls back on an empty list', () => {
    expect(highestEventId([], 0)).toBe(0);
  });
});
