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
