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

package cafe.jeffrey.timeseries;

import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;

/**
 * One series of per-second maxima over a recording's time range: every second of the range starts at
 * 0, and a second takes the largest value recorded in it. What a gauge sampled several times a second
 * (a heap size, a queue length, a rate) is drawn as.
 */
public final class MaxPerSecondSeries {

    /** A second nothing was recorded in holds 0, which is also what carrying forward replaces. */
    private static final long NOTHING_RECORDED = 0L;

    private final String name;
    private final LongLongHashMap values;

    public MaxPerSecondSeries(String name, RelativeTimeRange timeRange) {
        this.name = name;
        this.values = TimeseriesUtils.initWithZeros(timeRange);
    }

    public void record(long second, long value) {
        values.updateValue(second, NOTHING_RECORDED, existing -> Math.max(existing, value));
    }

    public SingleSerie build() {
        return TimeseriesUtils.buildSerie(name, values);
    }

    /**
     * The series with every second nothing was recorded in showing the value before it, rather than 0 —
     * for a gauge sampled less often than once a second, whose value holds between samples.
     */
    public SingleSerie buildCarryingForward() {
        SingleSerie serie = build();
        TimeseriesUtils.remapTimeseriesBySteps(serie, NOTHING_RECORDED);
        return serie;
    }
}
