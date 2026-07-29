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

import ProfileThreadClient from '@/services/api/ProfileThreadClient';
import type { ThreadEventState } from '@/services/api/model/ThreadEventDetail';
import type ThreadInfo from '@/services/api/model/ThreadInfo';
import type ThreadTimeWindow from '@/services/api/model/ThreadTimeWindow';
import type ThreadWindowEvents from '@/services/api/model/ThreadWindowEvents';

/**
 * Resolves what a tooltip shows, one hovered window at a time.
 *
 * Keyed by the window rather than by the band under the pointer, which is the whole point: a band
 * merges every run of activity too dense to draw apart, so on a busy lane there is one band for the
 * entire recording and asking about it answers the same thing at every position.
 *
 * Results are memoised so sweeping the pointer back and forth re-renders without re-querying. A
 * window comes from an integer pixel, so a row can only produce as many keys as it has pixels — the
 * cache is capped anyway, because a resize produces a fresh set of them.
 */
export default class ThreadWindowDetails {
  /**
   * Roughly a wide row's worth of pixels. Past that the oldest entry goes, which is the one furthest
   * from wherever the pointer has been working.
   */
  private static readonly MAX_CACHED_WINDOWS = 256;

  private readonly client: ProfileThreadClient;
  private readonly threadInfo: ThreadInfo;
  /**
   * Set on a collapsed lane, whose bands stand for a whole group of threads. The lane's own ids are
   * placeholders that match nothing, so the group is what the lookup goes by.
   */
  private readonly group: string | null;
  private readonly resolved = new Map<string, ThreadWindowEvents | null>();
  private readonly inFlight = new Map<string, Promise<ThreadWindowEvents | null>>();

  constructor(profileId: string, threadInfo: ThreadInfo, group: string | null = null) {
    this.client = new ProfileThreadClient(profileId);
    this.threadInfo = threadInfo;
    this.group = group;
  }

  /**
   * What is known about this window right now: `undefined` when it has not been fetched, `null` when
   * the lookup failed, otherwise the answer. The three are deliberately distinct — a failure that
   * read as "not fetched yet" would leave the tooltip waiting forever and re-arm the lookup on every
   * pointer move.
   */
  public cached(
    state: ThreadEventState,
    window: ThreadTimeWindow
  ): ThreadWindowEvents | null | undefined {
    const key = ThreadWindowDetails.key(state, window);
    if (!this.resolved.has(key)) {
      return undefined;
    }
    return this.touch(key);
  }

  /**
   * Fetches the window, reusing an in-flight or completed request for it. A failed lookup resolves
   * to `null` and is remembered, so a broken window does not retry on every hover.
   */
  public load(
    state: ThreadEventState,
    window: ThreadTimeWindow
  ): Promise<ThreadWindowEvents | null> {
    const key = ThreadWindowDetails.key(state, window);

    if (this.resolved.has(key)) {
      return Promise.resolve(this.touch(key));
    }

    const pending = this.inFlight.get(key);
    if (pending) {
      return pending;
    }

    const request = this.client
      .windowEvents(this.threadInfo, state, window, 1, this.group)
      .catch(() => null)
      .then(events => {
        this.inFlight.delete(key);
        this.remember(key, events);
        return events;
      });

    this.inFlight.set(key, request);
    return request;
  }

  /**
   * Drops everything. The windows are derived from the row's pixel width, so a resize invalidates
   * every one of them at once.
   */
  public clear(): void {
    this.resolved.clear();
    this.inFlight.clear();
  }

  /**
   * Re-inserts a key so it counts as the most recently used. `Map` iterates in insertion order, so
   * moving an entry to the end is what keeps the oldest one at the front for eviction.
   */
  private touch(key: string): ThreadWindowEvents | null {
    const events = this.resolved.get(key) as ThreadWindowEvents | null;
    this.resolved.delete(key);
    this.resolved.set(key, events);
    return events;
  }

  private remember(key: string, events: ThreadWindowEvents | null): void {
    this.resolved.set(key, events);
    while (this.resolved.size > ThreadWindowDetails.MAX_CACHED_WINDOWS) {
      const oldest = this.resolved.keys().next();
      if (oldest.done) {
        return;
      }
      this.resolved.delete(oldest.value);
    }
  }

  private static key(state: ThreadEventState, window: ThreadTimeWindow): string {
    return `${state}|${window.from}|${window.to}`;
  }
}
