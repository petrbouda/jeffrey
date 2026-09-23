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
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';
import TimeRange from '@/services/api/model/TimeRange';
import BothGraphData from '@/services/api/model/BothGraphData';
import GraphComponents from '@/services/api/model/GraphComponents';

export default abstract class FlamegraphClient {
  abstract provideBoth(
    composition: GraphComponents,
    timeRange: TimeRange | null,
    search: string | null
  ): Promise<BothGraphData>;

  abstract provide(timeRange: TimeRange | null): Promise<FlamegraphData>;

  abstract provideTimeseries(search: string | null): Promise<TimeseriesData>;

  abstract save(
    components: GraphComponents,
    flamegraphName: string,
    timeRange: TimeRange | null
  ): Promise<void>;

  /**
   * Whether this client re-fetches when the thread-mode/weight toggles change. Clients that support it
   * override this together with {@link setUseThreadMode}/{@link setUseWeight}; the default is a no-op so
   * updaters can drive the toggle polymorphically instead of type-checking concrete clients.
   */
  supportsModeToggle(): boolean {
    return false;
  }

  setUseThreadMode(_value: boolean): void {}

  setUseWeight(_value: boolean | null): void {}
}
