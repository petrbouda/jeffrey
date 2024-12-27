/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.calculated.nativeleak.summary;

import pbouda.jeffrey.calculated.api.EventSummaryCalculator;
import pbouda.jeffrey.calculated.nativeleak.collector.NativeAllocations;
import pbouda.jeffrey.calculated.nativeleak.collector.NativeLeaks;
import pbouda.jeffrey.calculated.nativeleak.raw.NativeLeakRawDataProvider;
import pbouda.jeffrey.calculated.api.RawDataProvider;
import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.EventSummary;
import pbouda.jeffrey.common.Type;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class NativeLeakEventSummaryCalculator implements EventSummaryCalculator {

    private static final Map<String, String> EXTRAS = Map.ofEntries(
            Map.entry("source", EventSource.ASYNC_PROFILER.getLabel()),
            Map.entry("type", Type.NATIVE_LEAK.code()),
            Map.entry("calculated", Boolean.TRUE.toString())
    );

    private static final List<String> CATEGORIES = List.of("Calculated");

    private static final String NATIVE_LEAK_LABEL = "Native Memory Leak";
    private final RawDataProvider<NativeLeaks> dataProvider;

    public NativeLeakEventSummaryCalculator(List<Path> recordings) {
        this(new NativeLeakRawDataProvider(recordings));
    }

    public NativeLeakEventSummaryCalculator(RawDataProvider<NativeLeaks> dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Override
    public EventSummary calculate() {
        NativeLeaks data = dataProvider.provide();

        long samples = data.leaks().size();
        long weight = data.leaks().stream()
                .mapToLong(Type.MALLOC.weight().extractor())
                .sum();

        return new EventSummary(
                Type.NATIVE_LEAK.code(),
                NATIVE_LEAK_LABEL,
                samples,
                weight,
                true,
                CATEGORIES,
                EXTRAS);
    }
}
