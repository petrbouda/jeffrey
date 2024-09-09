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

package pbouda.jeffrey.generator.flamegraph.flame;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.frameir.processor.EventProcessors;
import pbouda.jeffrey.generator.flamegraph.GraphGenerator;
import pbouda.jeffrey.generator.flamegraph.collector.FrameCollectorFactories;
import pbouda.jeffrey.jfrparser.jdk.RecordingIterators;

import java.util.List;

public class FlamegraphGeneratorImpl implements GraphGenerator {

    @Override
    public ObjectNode generate(Config config) {
        return generate(config, List.of());
    }

    @Override
    public ObjectNode generate(Config config, List<Marker> markers) {
        if (config.eventType().isAllocationTlab()) {
            return RecordingIterators.automaticAndCollect(
                    config.primaryRecordings(),
                    EventProcessors.allocationTlab(config.primaryTimeRange(), config.threadMode()),
                    FrameCollectorFactories.allocJson(markers));

        } else if (config.eventType().isAllocationSamples()) {
            return RecordingIterators.automaticAndCollect(
                    config.primaryRecordings(),
                    EventProcessors.allocationSamples(config.primaryTimeRange(), config.threadMode()),
                    FrameCollectorFactories.allocJson(markers));

        } else if (Type.JAVA_MONITOR_ENTER.equals(config.eventType())) {
            return generateMonitorTree(config, markers, Type.JAVA_MONITOR_ENTER);
        } else if (Type.JAVA_MONITOR_WAIT.equals(config.eventType())) {
            return generateMonitorTree(config, markers, Type.JAVA_MONITOR_WAIT);
        } else if (Type.THREAD_PARK.equals(config.eventType())) {
            return generateMonitorTree(config, markers, Type.THREAD_PARK);
        } else {
            return RecordingIterators.automaticAndCollect(
                    config.primaryRecordings(),
                    EventProcessors.simple(config),
                    FrameCollectorFactories.simpleJson(markers));
        }
    }

    private static ObjectNode generateMonitorTree(Config config, List<Marker> markers, Type eventType) {
        return RecordingIterators.automaticAndCollect(
                config.primaryRecordings(),
                EventProcessors.blocking(config, eventType),
                FrameCollectorFactories.blockingJson(markers));
    }
}
