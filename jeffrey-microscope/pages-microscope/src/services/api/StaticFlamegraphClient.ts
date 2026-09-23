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

import FlamegraphData from '@/services/api/model/FlamegraphData';
import FlamegraphClient from '@/services/api/FlamegraphClient';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import GraphComponents from '@/services/api/model/GraphComponents';
import TimeRange from '@/services/api/model/TimeRange';
import BothGraphData from '@/services/api/model/BothGraphData';

export default class StaticFlamegraphClient extends FlamegraphClient {
  private readonly flamegraphData: FlamegraphData;
  private readonly timeseriesData: TimeseriesData;

  constructor(bothGraphData: BothGraphData) {
    super();
    this.flamegraphData = bothGraphData.flamegraph;
    this.timeseriesData = bothGraphData.timeseries;
  }

  provideBoth(
    _composition: GraphComponents,
    _timeRange: TimeRange | null,
    _search: string | null
  ): Promise<BothGraphData> {
    return Promise.resolve(new BothGraphData(this.flamegraphData, this.timeseriesData));
  }

  provide(_timeRange: any): Promise<FlamegraphData> {
    return Promise.resolve(this.flamegraphData);
  }

  provideTimeseries(_search: string | null): Promise<TimeseriesData> {
    return Promise.resolve(this.timeseriesData);
  }

  save(
    _components: GraphComponents,
    _flamegraphName: string,
    _timeRange: TimeRange | null
  ): Promise<void> {
    console.error('Cannot export flamegraph from statically generated data');
    return Promise.resolve();
  }
}
