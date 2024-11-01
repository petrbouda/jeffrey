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
import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.frameir.processor.EventProcessors;
import pbouda.jeffrey.generator.flamegraph.GraphGenerator;
import pbouda.jeffrey.generator.flamegraph.collector.FrameCollectorFactories;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.settings.ActiveSettingsProvider;

import java.util.List;

public class FlamegraphGeneratorImpl implements GraphGenerator {

    private final ActiveSettingsProvider settingsProvider;

    public FlamegraphGeneratorImpl(ActiveSettingsProvider settingsProvider) {
        this.settingsProvider = settingsProvider;
    }

    @Override
    public ObjectNode generate(Config config) {
        return generate(config, List.of());
    }

    @Override
    public ObjectNode generate(Config config, List<Marker> markers) {
        settingsProvider.get();

        if (config.eventType().isAllocationEvent()) {
            return JdkRecordingIterators.automaticAndCollect(
                    config.primaryRecordings(),
                    EventProcessors.allocationSamples(config),
                    FrameCollectorFactories.allocJson(markers));
        } else if (config.eventType().isBlockingEvent()) {
            return JdkRecordingIterators.automaticAndCollect(
                    config.primaryRecordings(),
                    EventProcessors.blocking(config),
                    FrameCollectorFactories.blockingJson(markers));
        } else {
            return JdkRecordingIterators.automaticAndCollect(
                    config.primaryRecordings(),
                    EventProcessors.cpuSamples(config),
                    FrameCollectorFactories.simpleJson(markers));
        }
    }
}
