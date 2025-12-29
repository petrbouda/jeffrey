/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.timeseries;

import org.eclipse.collections.api.tuple.primitive.LongLongPair;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.shared.model.time.RelativeTimeRange;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class TimeseriesUtils {
    private record StartEnd(long start, long end) {
    }

    public static LongLongHashMap init(RelativeTimeRange timeRange, ChronoUnit unit) {
        StartEnd startEnd = resolveStartEnd(timeRange, unit);

        LongLongHashMap values = new LongLongHashMap();
        values.put(startEnd.start, 0);
        values.put(startEnd.end, 0);
        return values;
    }

    public static LongLongHashMap initWithZeros(RelativeTimeRange timeRange) {
        return initWithZeros(timeRange, ChronoUnit.SECONDS, 0);
    }

    public static LongLongHashMap initWithZeros(RelativeTimeRange timeRange, ChronoUnit unit) {
        return initWithZeros(timeRange, unit, 0);
    }

    public static LongLongHashMap initWithZeros(RelativeTimeRange timeRange, long defaultValue) {
        return initWithZeros(timeRange, ChronoUnit.SECONDS, defaultValue);
    }

    public static LongLongHashMap initWithZeros(RelativeTimeRange timeRange, ChronoUnit unit, long defaultValue) {
        StartEnd startEnd = resolveStartEnd(timeRange, unit);

        LongLongHashMap values = new LongLongHashMap();
        for (long i = startEnd.start; i <= startEnd.end; ++i) {
            values.put(i, defaultValue);
        }
        return values;
    }

    private static StartEnd resolveStartEnd(RelativeTimeRange timeRange, ChronoUnit unit) {
        Duration truncatedStart = timeRange.start().truncatedTo(unit);
        Duration truncatedEnd = timeRange.end().truncatedTo(unit);

        long start;
        long end;
        if (ChronoUnit.SECONDS.equals(unit)) {
            start = truncatedStart.toSeconds();
            end = truncatedEnd.toSeconds();
        } else if (ChronoUnit.MILLIS.equals(unit)) {
            start = truncatedStart.toMillis();
            end = truncatedEnd.toMillis();
        } else {
            throw new IllegalArgumentException("Unsupported ChronoUnit: " + unit);
        }

        return new StartEnd(start, end);
    }

    public static TimeseriesData differential(TimeseriesData primary, TimeseriesData secondary) {
        return new TimeseriesData(
                new SingleSerie("Primary Samples", primary.series().getFirst().data()),
                new SingleSerie("Secondary Samples", secondary.series().getFirst().data()));
    }

    public static SingleSerie buildSerie(String serieName, LongLongHashMap values) {
        List<List<Long>> sorted = values.keyValuesView()
                .toSortedList(Comparator.comparing(LongLongPair::getOne))
                .collect(pair -> {
                    List<Long> list = new ArrayList<>(2);
                    list.add(pair.getOne());
                    list.add(pair.getTwo());
                    return list;
                });

        return new SingleSerie(serieName, sorted);
    }

    public static void remapTimeseriesBySteps(SingleSerie serie, long markValue) {
        List<List<Long>> points = serie.data();
        for (int i = 1; i < points.size(); i++) {
            List<Long> currentPoint = points.get(i);
            if (currentPoint.get(1) == markValue) {
                List<Long> prevPoint = points.get(i - 1);
                Long prevValue = prevPoint.get(1);
                currentPoint.set(1, prevValue);
            }
        }
    }

    public static void toAbsoluteTime(TimeseriesData data, long recordingStart) {
        for (SingleSerie series : data.series()) {
            for (List<Long> point : series.data()) {
                long newValue = (point.getFirst() * 1000) + recordingStart;
                point.set(0, newValue);
            }
        }
    }
}
