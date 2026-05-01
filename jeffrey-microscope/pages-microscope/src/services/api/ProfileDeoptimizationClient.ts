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
