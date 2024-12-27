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

package pbouda.jeffrey.calculated.nativeleak.timeseries;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.calculated.api.RawDataProvider;
import pbouda.jeffrey.calculated.nativeleak.collector.NativeLeaks;
import pbouda.jeffrey.calculated.nativeleak.raw.NativeLeakRawDataProvider;
import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.jfrparser.api.EventProcessor;
import pbouda.jeffrey.timeseries.iterator.EventProcessingIterator;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class NativeLeakEventProcessingIterator implements EventProcessingIterator {

    private final RawDataProvider<NativeLeaks> dataProvider;

    public NativeLeakEventProcessingIterator(List<Path> recordings) {
        this(new NativeLeakRawDataProvider(recordings));
    }

    public NativeLeakEventProcessingIterator(RawDataProvider<NativeLeaks> dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Override
    public <PARTIAL, RESULT> RESULT iterate(
            Supplier<? extends EventProcessor<PARTIAL>> processorSupplier,
            Collector<PARTIAL, RESULT> collector) {

        NativeLeaks data = dataProvider.provide();
        EventProcessor<PARTIAL> processor = processorSupplier.get();
        for (RecordedEvent leak : data.leaks()) {
            processor.onEvent(leak);
        }
        return collector.finisher(processor.get());
    }
}
