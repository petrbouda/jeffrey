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
import type ThreadEventDetail from '@/services/api/model/ThreadEventDetail';
import type { ThreadEventState } from '@/services/api/model/ThreadEventDetail';
import type ThreadInfo from '@/services/api/model/ThreadInfo';
import type ThreadPeriod from '@/services/api/model/ThreadPeriod';

/**
 * Resolves the field values a band's tooltip shows, one band at a time.
 *
 * The timeline ships rectangles without any event fields, so hovering a band is what triggers the
 * lookup. Results are memoised per band: moving the pointer back and forth over the same row must
 * not re-query, and a band that is already resolved renders with no flicker at all.
 */
export default class ThreadBandDetails {
  private readonly client: ProfileThreadClient;
  private readonly threadInfo: ThreadInfo;
  private readonly resolved = new Map<string, ThreadEventDetail | null>();
  private readonly inFlight = new Map<string, Promise<ThreadEventDetail | null>>();

  constructor(profileId: string, threadInfo: ThreadInfo) {
    this.client = new ProfileThreadClient(profileId);
    this.threadInfo = threadInfo;
  }

  /**
   * The values already known for this band, or `undefined` while they still have to be fetched.
   */
  public cached(state: ThreadEventState, band: ThreadPeriod): Array<string> | undefined {
    const detail = this.resolved.get(ThreadBandDetails.key(state, band));
    if (detail === undefined || detail === null) {
      return undefined;
    }
    return detail.values;
  }

  /**
   * Fetches the first event of the band, reusing an in-flight or completed request for it. A failed
   * lookup resolves to `null` and is remembered, so a broken band does not retry on every hover.
   */
  public load(state: ThreadEventState, band: ThreadPeriod): Promise<ThreadEventDetail | null> {
    const key = ThreadBandDetails.key(state, band);

    const alreadyResolved = this.resolved.get(key);
    if (alreadyResolved !== undefined) {
      return Promise.resolve(alreadyResolved);
    }

    const pending = this.inFlight.get(key);
    if (pending) {
      return pending;
    }

    const request = this.client
      .bandEvents(this.threadInfo, state, band)
      .then(events => (events.length > 0 ? events[0] : null))
      .catch(() => null)
      .then(detail => {
        this.resolved.set(key, detail);
        this.inFlight.delete(key);
        return detail;
      });

    this.inFlight.set(key, request);
    return request;
  }

  private static key(state: ThreadEventState, band: ThreadPeriod): string {
    return `${state}|${band.startOffset}|${band.width}`;
  }
}
