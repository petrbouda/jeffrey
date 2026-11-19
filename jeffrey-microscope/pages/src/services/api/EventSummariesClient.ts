/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
import EventSummary from '@/services/api/model/EventSummary';
import FlamegraphPanel from '@/services/api/model/FlamegraphPanel';

export default class EventSummariesClient extends BaseProfileClient {
  private constructor(profileId: string, featurePath: string) {
    super(profileId, featurePath);
  }

  /**
   * Create a client for primary profile event summaries.
   */
  static primary(profileId: string): EventSummariesClient {
    return new EventSummariesClient(profileId, 'flamegraph');
  }

  /**
   * Create a client for differential analysis event summaries.
   */
  static differential(primaryProfileId: string, secondaryProfileId: string): EventSummariesClient {
    return new EventSummariesClient(
      primaryProfileId,
      `diff/${secondaryProfileId}/differential-flamegraph`
    );
  }

  events(): Promise<EventSummary[]> {
    return super.get<EventSummary[]>('/events');
  }

  panels(): Promise<FlamegraphPanel[]> {
    return super.get<FlamegraphPanel[]>('/panels');
  }
}
