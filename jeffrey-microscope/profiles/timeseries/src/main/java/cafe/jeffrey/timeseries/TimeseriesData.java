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

package cafe.jeffrey.timeseries;

import java.util.List;

public record TimeseriesData(List<SingleSerie> series) {

    public TimeseriesData(SingleSerie serie1, SingleSerie serie2, SingleSerie serie3) {
        this(List.of(serie1, serie2, serie3));
    }

    public TimeseriesData(SingleSerie serie1, SingleSerie serie2) {
        this(List.of(serie1, serie2));
    }

    public TimeseriesData(SingleSerie serie) {
        this(List.of(serie));
    }

    public static TimeseriesData empty() {
        return new TimeseriesData(List.of());
    }
}
