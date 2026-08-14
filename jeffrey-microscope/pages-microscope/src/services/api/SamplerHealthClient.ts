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
import type CpuTimeSampleLoss from '@/services/api/model/CpuTimeSampleLoss';

/**
 * Completeness of the profile's sampled data, as the samplers themselves reported it.
 */
export default class SamplerHealthClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'sampler-health');
  }

  /**
   * Advisory data only — a failure here must not raise a toast over the view that asked for it.
   */
  public cpuTimeSampleLoss(): Promise<CpuTimeSampleLoss> {
    return this.get<CpuTimeSampleLoss>('/cpu-time-sample-loss', undefined, { suppressToast: true });
  }
}
