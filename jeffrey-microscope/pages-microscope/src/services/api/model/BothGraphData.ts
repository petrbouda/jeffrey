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

import FlamegraphData from '@/services/api/model/FlamegraphData';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';

/**
 * A graph response: the parts the request asked for, null for the ones it did not.
 */
export default class BothGraphData {
  constructor(
    public flamegraph: FlamegraphData | null,
    public timeseries: TimeseriesData | null
  ) {}

  /** The flamegraph of a response that was asked for one; its absence is a server fault. */
  requireFlamegraph(): FlamegraphData {
    if (this.flamegraph === null) {
      throw new Error('Graph response carries no flamegraph');
    }
    return this.flamegraph;
  }

  /** The timeseries of a response that was asked for one; its absence is a server fault. */
  requireTimeseries(): TimeseriesData {
    if (this.timeseries === null) {
      throw new Error('Graph response carries no timeseries');
    }
    return this.timeseries;
  }
}
