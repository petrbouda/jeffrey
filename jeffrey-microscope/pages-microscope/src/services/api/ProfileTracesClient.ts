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
import FlamegraphPanel from '@/services/api/model/FlamegraphPanel';
import type {
  TraceDetail,
  TraceEventRow,
  TraceOperationRow,
  TraceOverview,
  TraceRow
} from '@/services/api/model/trace/TraceModels';

export default class ProfileTracesClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'traces');
  }

  public getTraces(limit?: number): Promise<TraceRow[]> {
    return this.get<TraceRow[]>('', limit === undefined ? undefined : { limit });
  }

  /** Profile-wide totals, which the capped trace list cannot be summed into. */
  public getOverview(): Promise<TraceOverview> {
    return this.get<TraceOverview>('/overview');
  }

  public getTrace(traceId: string): Promise<TraceDetail> {
    return this.get<TraceDetail>(`/${traceId}`);
  }

  public getOperations(limit?: number): Promise<TraceOperationRow[]> {
    return this.get<TraceOperationRow[]>(
      '/operations',
      limit === undefined ? undefined : { limit }
    );
  }

  /** What the JVM was doing on the span's thread while it was open. */
  public getSpanEvents(traceId: string, spanId: string): Promise<TraceEventRow[]> {
    return this.get<TraceEventRow[]>(`/${traceId}/spans/${spanId}/events`);
  }

  /**
   * Which event types actually recorded samples inside the span, with their real counts, so the
   * drill-down offers only flamegraphs that exist.
   */
  public getSpanPanels(
    traceId: string,
    spanId: string,
    selfOnly: boolean
  ): Promise<FlamegraphPanel[]> {
    return this.get<FlamegraphPanel[]>(`/${traceId}/spans/${spanId}/panels`, { selfOnly });
  }
}
