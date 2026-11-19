/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
