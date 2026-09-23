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

package cafe.jeffrey.profile.manager.registry;

import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.SubSecondManager;
import cafe.jeffrey.profile.manager.TimeseriesManager;

public record VisualizationFactories(
        FlamegraphManager.Factory flamegraph,
        FlamegraphManager.DifferentialFactory flamegraphDiff,
        SubSecondManager.Factory subSecond,
        TimeseriesManager.Factory timeseries,
        TimeseriesManager.DifferentialFactory timeseriesDiff) {
}
