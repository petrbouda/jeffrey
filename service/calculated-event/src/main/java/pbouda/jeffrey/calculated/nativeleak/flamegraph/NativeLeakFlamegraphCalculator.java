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

package pbouda.jeffrey.calculated.nativeleak.flamegraph;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.calculated.api.FlamegraphEventCalculator;
import pbouda.jeffrey.calculated.api.RawDataProvider;
import pbouda.jeffrey.calculated.nativeleak.collector.NativeLeaks;
import pbouda.jeffrey.calculated.nativeleak.raw.NativeLeakRawDataProvider;
import pbouda.jeffrey.common.BytesFormatter;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.flamegraph.FlameGraphBuilder;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.record.SimpleRecord;
import pbouda.jeffrey.frameir.tree.SimpleTreeBuilder;
import pbouda.jeffrey.jfrparser.jdk.type.JdkStackTrace;
import pbouda.jeffrey.jfrparser.jdk.type.JdkThread;

import java.nio.file.Path;
import java.util.List;

public class NativeLeakFlamegraphCalculator implements FlamegraphEventCalculator {

    private final RawDataProvider<NativeLeaks> dataProvider;

    public NativeLeakFlamegraphCalculator(List<Path> recordings) {
        this(new NativeLeakRawDataProvider(recordings));
    }

    public NativeLeakFlamegraphCalculator(RawDataProvider<NativeLeaks> dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Override
    public ObjectNode calculate(Config config) {
        SimpleTreeBuilder treeBuilder = new SimpleTreeBuilder(
                config.graphParameters().threadMode(),
                false);

        NativeLeaks data = this.dataProvider.provide();
        data.leaks().stream()
                .map(NativeLeakFlamegraphCalculator::mapEvent)
                .forEach(treeBuilder::addRecord);

        Frame frame = treeBuilder.build();

        FlameGraphBuilder graphBuilder = new FlameGraphBuilder(
                false, weight -> BytesFormatter.format(weight) + " Allocated");
        return graphBuilder.apply(frame);
    }

    private static SimpleRecord mapEvent(RecordedEvent event) {
        return new SimpleRecord(
                new JdkStackTrace(event.getStackTrace()),
                new JdkThread(event),
                1,
                event.getLong("size"));
    }
}
