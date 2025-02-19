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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class TimeseriesUtils {
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

    public static void toAbsoluteTime(TimeseriesData data, long recordingStart) {
        for (SingleSerie series : data.series()) {
            for (List<Long> point : series.data()) {
                long newValue = point.getFirst() + recordingStart;
                point.set(0, newValue);
            }
        }
    }
}
