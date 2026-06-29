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

import BaseProfileClient from '@/services/api/BaseProfileClient';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import TimeRange from '@/services/api/model/TimeRange';

/**
 * Generic per-event timeseries endpoint (`POST /profiles/{id}/timeseries`). Unlike the feature
 * clients it accepts a `timeRange` (relative millis window) and a `targetBuckets` cap, so the
 * backend aggregates server-side into a bounded number of points (plus a peak series) — keeping
 * long recordings readable instead of shipping one point per second.
 */
export default class ProfileTimeseriesClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'timeseries');
  }

  /**
   * @param eventType     event type code (e.g. `jdk.ExecutionSample`)
   * @param timeRange     window to restrict to, or null for the whole recording
   * @param targetBuckets maximum number of points to return
   */
  getTimeseries(
    eventType: string,
    timeRange: TimeRange | null,
    targetBuckets: number
  ): Promise<TimeseriesData> {
    return this.post<TimeseriesData>('', {
      eventType: eventType,
      search: null,
      useWeight: false,
      excludeNonJavaSamples: false,
      excludeIdleSamples: false,
      onlyUnsafeAllocationSamples: false,
      threadInfo: null,
      markers: [],
      timeRange: timeRange,
      targetBuckets: targetBuckets
    });
  }
}
