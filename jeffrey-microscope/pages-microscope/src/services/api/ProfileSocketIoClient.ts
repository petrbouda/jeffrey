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
import IoMetric from '@/services/api/model/IoMetric';
import type {
  IoEndpoint,
  IoEndpointTimeline,
  IoOperation,
  IoOverview
} from '@/services/api/model/IoModels';

export default class ProfileSocketIoClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'io/socket');
  }

  public getOverview(): Promise<IoOverview> {
    return this.get<IoOverview>('');
  }

  /**
   * Throughput across every peer, or scoped to a single {@code host:port} peer when one is given.
   */
  public getTimeline(peer?: string): Promise<TimeseriesData> {
    return this.get<TimeseriesData>('/timeline', peer ? { target: peer } : undefined);
  }

  /**
   * The top peers for the given metric, each with its own per-second series for the sparkline
   * gallery. The metric picks the ranking as well as the series, and the server ranks before it
   * caps — so BYTES and COUNT return different peers, not the same peers reordered.
   */
  public getPeerTimelines(metric: IoMetric = IoMetric.BYTES): Promise<IoEndpointTimeline[]> {
    return this.get<IoEndpointTimeline[]>('/endpoint-timelines', { metric });
  }

  public getSlowest(): Promise<IoOperation[]> {
    return this.get<IoOperation[]>('/slowest');
  }

  public getPeers(): Promise<IoEndpoint[]> {
    return this.get<IoEndpoint[]>('/endpoints');
  }
}
