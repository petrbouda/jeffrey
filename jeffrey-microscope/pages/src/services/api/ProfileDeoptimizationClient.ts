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
import type JITDeoptimizationEvent from '@/services/api/model/JITDeoptimizationEvent';
import type JITDeoptimizationMethodAggregate from '@/services/api/model/JITDeoptimizationMethodAggregate';
import type JITDeoptimizationReasonCount from '@/services/api/model/JITDeoptimizationReasonCount';
import type JITDeoptimizationStats from '@/services/api/model/JITDeoptimizationStats';
import Serie from '@/services/timeseries/model/Serie';

export default class ProfileDeoptimizationClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'deoptimization');
  }

  public getStatistics(): Promise<JITDeoptimizationStats> {
    return this.get<JITDeoptimizationStats>('/statistics');
  }

  public getTimeseries(): Promise<Serie> {
    return this.get<Serie>('/timeseries');
  }

  public getEvents(): Promise<JITDeoptimizationEvent[]> {
    return this.get<JITDeoptimizationEvent[]>('/events');
  }

  public getTopMethods(): Promise<JITDeoptimizationMethodAggregate[]> {
    return this.get<JITDeoptimizationMethodAggregate[]>('/top-methods');
  }

  public getReasonDistribution(): Promise<JITDeoptimizationReasonCount[]> {
    return this.get<JITDeoptimizationReasonCount[]>('/reason-distribution');
  }
}
