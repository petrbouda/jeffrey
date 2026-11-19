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
import FlamegraphPanel from '@/services/api/model/FlamegraphPanel';
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
   * The flamegraph card grid scoped to the spans of the given tag, so the cards show the real
   * sample/weight counts those spans cover (not the profile-wide totals).
   */
  public getPanels(tag: string): Promise<FlamegraphPanel[]> {
    return this.get<FlamegraphPanel[]>('/spans/panels', { tag });
  }
}
