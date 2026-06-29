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

package cafe.jeffrey.timeseries;

import java.util.ArrayList;
import java.util.List;

/**
 * Reduces a per-second timeseries to at most a target number of points so long recordings stay
 * readable, instead of shipping tens of thousands of points and decimating (and losing spikes) on
 * the client. Consecutive points are grouped into equal-width buckets; each bucket's value is the
 * SUM of the grouped values, so no sample mass is lost. Multi-series inputs (e.g. differential or
 * matched/total splits) keep their series count and pairing.
 */
public final class TimeseriesDownsampler {

    private static final int TIME_INDEX = 0;
    private static final int VALUE_INDEX = 1;

    private TimeseriesDownsampler() {
    }

    public static TimeseriesData downsample(TimeseriesData data, int targetBuckets) {
        if (targetBuckets <= 0) {
            return data;
        }

        List<SingleSerie> result = new ArrayList<>();
        for (SingleSerie serie : data.series()) {
            result.add(reduce(serie.name(), serie.data(), targetBuckets));
        }
        return new TimeseriesData(result);
    }

    private static SingleSerie reduce(String name, List<List<Long>> points, int targetBuckets) {
        int size = points.size();
        if (size <= targetBuckets) {
            // Already within budget: every bucket holds a single point.
            List<List<Long>> copy = new ArrayList<>(size);
            for (List<Long> point : points) {
                copy.add(point(point.get(TIME_INDEX), point.get(VALUE_INDEX)));
            }
            return new SingleSerie(name, copy);
        }

        int groupSize = (int) Math.ceil((double) size / targetBuckets);
        List<List<Long>> reduced = new ArrayList<>();
        for (int start = 0; start < size; start += groupSize) {
            int end = Math.min(start + groupSize, size);
            long bucketTime = points.get(start).get(TIME_INDEX);
            long sum = 0L;
            for (int i = start; i < end; i++) {
                sum += points.get(i).get(VALUE_INDEX);
            }
            reduced.add(point(bucketTime, sum));
        }
        return new SingleSerie(name, reduced);
    }

    private static List<Long> point(long time, long value) {
        List<Long> point = new ArrayList<>(2);
        point.add(time);
        point.add(value);
        return point;
    }
}
