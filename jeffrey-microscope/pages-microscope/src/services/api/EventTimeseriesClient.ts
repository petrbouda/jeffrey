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

/**
 * Thin client over the generic timeseries endpoint
 * (POST /api/internal/profiles/{profileId}/timeseries).
 *
 * Used by the Recording Dashboard to render an "events over time" sparkline for
 * a single event type. The endpoint deserializes {@code eventType} from the
 * event-type code string (e.g. "jdk.ExecutionSample").
 */
export default class EventTimeseriesClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'timeseries');
  }

  /**
   * Generates a sample-count timeseries for a single event type.
   * @param eventTypeCode the event-type code (e.g. "jdk.ExecutionSample")
   */
  public forEventType(eventTypeCode: string): Promise<TimeseriesData> {
    return this.post<TimeseriesData>(
      '',
      {
        eventType: eventTypeCode,
        search: null,
        useWeight: false,
        excludeNonJavaSamples: false,
        excludeIdleSamples: false,
        onlyUnsafeAllocationSamples: false,
        threadInfo: null,
        markers: []
      },
      { suppressToast: true }
    );
  }
}
