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
import EventSummary from '@/services/api/model/EventSummary';
import type {
  SpanDetailRow,
  SpanEventRow,
  SpanOverview,
  SpanSlowestRow,
  SpanTagStat
} from '@/services/api/model/span/SpanModels';

export default class ProfileAsyncProfilerClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'async-profiler');
  }

  public getOverview(): Promise<SpanOverview> {
    return this.get<SpanOverview>('/spans/overview');
  }

  public getTagStats(): Promise<SpanTagStat[]> {
    return this.get<SpanTagStat[]>('/spans/tags');
  }

  public getTagSpans(tag: string): Promise<SpanDetailRow[]> {
    return this.get<SpanDetailRow[]>('/spans/tag', { tag });
  }

  public getSlowestSpans(): Promise<SpanSlowestRow[]> {
    return this.get<SpanSlowestRow[]>('/spans/slowest');
  }

  public getSpanEvents(
    threadHash: string,
    fromMillis: number,
    toMillis: number
  ): Promise<SpanEventRow[]> {
    return this.get<SpanEventRow[]>('/spans/events', { threadHash, fromMillis, toMillis });
  }

  /**
   * Per-event-type summaries scoped to the spans of the given tag, so the flamegraph cards show the
   * real sample/weight counts those spans cover (not the profile-wide totals).
   */
  public getEventSummaries(tag: string): Promise<EventSummary[]> {
    return this.get<EventSummary[]>('/spans/event-summaries', { tag });
  }
}
