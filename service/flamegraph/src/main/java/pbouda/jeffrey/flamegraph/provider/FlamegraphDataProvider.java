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

package pbouda.jeffrey.flamegraph.builder;

import pbouda.jeffrey.common.BytesFormatter;
import pbouda.jeffrey.common.DurationFormatter;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.time.RelativeTimeRange;
import pbouda.jeffrey.flamegraph.FlameGraphBuilder;
import pbouda.jeffrey.flamegraph.api.FlamegraphData;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.frame.AllocationTopFrameProcessor;
import pbouda.jeffrey.frameir.tree.AllocationTreeBuilder;
import pbouda.jeffrey.frameir.tree.BlockingTreeBuilder;
import pbouda.jeffrey.frameir.tree.SimpleTreeBuilder;
import pbouda.jeffrey.jfrparser.api.RecordBuilder;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.streamer.EventStreamer;
import pbouda.jeffrey.provider.api.streamer.model.FlamegraphRecord;

public class FlamegraphDataProviderImpl implements FlamegraphDataProvider {

    private final ProfileEventRepository eventRepository;
    private final Config config;
    private final FlameGraphBuilder flameGraphBuilder;
    private final RecordBuilder<FlamegraphRecord, Frame> frameTreeBuilder;

    public FlamegraphDataProviderImpl(ProfileEventRepository eventRepository, Config config) {
        GraphParameters params = config.graphParameters();

        this.eventRepository = eventRepository;
        this.config = config;
        this.flameGraphBuilder = new FlameGraphBuilder(!params.markers().isEmpty());
        this.frameTreeBuilder = new SimpleTreeBuilder(false, params.threadMode(), params.parseLocations());
    }

    @Override
    public FlamegraphData provide() {
        GraphParameters params = config.graphParameters();

        EventStreamer<FlamegraphRecord> eventStreamer = eventRepository.newEventStreamerFactory(config.eventType())
                .newFlamegraphStreamer()
                .stacktraces(params.stacktraceTypes())
                .stacktraceTags(params.stacktraceTags())
                .threads(params.threadMode(), config.threadInfo());

        RelativeTimeRange timeRange = config.timeRange();
        if (timeRange.isStartUsed()) {
            eventStreamer = eventStreamer.from(timeRange.start());
        }
        if (timeRange.isEndUsed()) {
            eventStreamer = eventStreamer.until(timeRange.end());
        }

        eventStreamer.startStreaming()
                .forEach(frameTreeBuilder::onRecord);

        Frame frame = frameTreeBuilder.build();
        return flameGraphBuilder.build(frame);
    }

    public static RecordBuilders simple(GraphParameters params, boolean handleLambdas) {
        FlameGraphBuilder flameGraphBuilder = new FlameGraphBuilder(!params.markers().isEmpty());
        RecordBuilder<FlamegraphRecord, Frame> frameTreeBuilder =
                new SimpleTreeBuilder(handleLambdas, params.threadMode(), params.parseLocations());
    }

    public static RecordBuilders allocation(GraphParameters params, boolean handleLambdas) {
        FlameGraphBuilder flameGraphBuilder = new FlameGraphBuilder(
                !params.markers().isEmpty(), weight -> BytesFormatter.format(weight) + " Allocated");
        RecordBuilder<FlamegraphRecord, Frame> frameTreeBuilder =
                new AllocationTreeBuilder(
                        handleLambdas, params.threadMode(), params.parseLocations(), new AllocationTopFrameProcessor());
    }

    public static RecordBuilders blocking(GraphParameters params) {
        FlameGraphBuilder flameGraphBuilder = new FlameGraphBuilder(
                !params.markers().isEmpty(), weight -> DurationFormatter.format(weight) + " Blocked");
        RecordBuilder<FlamegraphRecord, Frame> frameTreeBuilder =
                new BlockingTreeBuilder(params.threadMode(), params.parseLocations());
    }
}
