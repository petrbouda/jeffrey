/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
import MethodTracingOverviewData from '@/services/api/model/MethodTracingOverviewData';
import MethodTracingSlowestData from '@/services/api/model/MethodTracingSlowestData';
import MethodTracingCumulatedData from '@/services/api/model/MethodTracingCumulatedData';
import MethodTimingData from '@/services/api/model/MethodTimingData';

export default class ProfileMethodTracingClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'method-tracing');
  }

  public getOverview(): Promise<MethodTracingOverviewData> {
    return this.get<MethodTracingOverviewData>('/overview');
  }

  public getSlowest(): Promise<MethodTracingSlowestData> {
    return this.get<MethodTracingSlowestData>('/slowest');
  }

  public getCumulated(mode: 'method' | 'class'): Promise<MethodTracingCumulatedData> {
    return this.get<MethodTracingCumulatedData>('/cumulated', { mode });
  }

  /**
   * What `jdk.MethodTiming` counted — exact per-method tallies, as opposed to the traced
   * invocations every other endpoint here reads.
   */
  public getTiming(): Promise<MethodTimingData> {
    return this.get<MethodTimingData>('/timing');
  }
}
