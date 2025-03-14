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

package pbouda.jeffrey.flamegraph.provider;

import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.flamegraph.FlameGraphBuilder;
import pbouda.jeffrey.flamegraph.api.FlamegraphData;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.FrameBuilder;
import pbouda.jeffrey.frameir.FrameBuilderResolver;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.streamer.EventStreamConfigurer;

public class FlamegraphDataProvider {

    private final ProfileEventRepository eventRepository;
    private final Config config;
    private final FrameBuilder frameBuilder;
    private final FlameGraphBuilder flamegraphBuilder;

    private FlamegraphDataProvider(ProfileEventRepository eventRepository, Config config, FrameBuilder frameBuilder) {
        this.eventRepository = eventRepository;
        this.config = config;
        this.frameBuilder = frameBuilder;
        this.flamegraphBuilder = resolveFlamegraphBuilder(config);
    }

    /**
     * Creates a new instance of the {@link FlamegraphDataProvider} for the primary mode. It automatically resolves the
     * {@link FrameBuilder} based on the event type and the graph parameters. Then it starts processing the records
     * from the event repository and builds the flamegraph.
     *
     * @param eventRepository repository to fetch all the records for processing
     * @param config          configuration for the flamegraph.
     * @return instance of the {@link FlamegraphDataProvider}.
     */
    public static FlamegraphDataProvider primary(ProfileEventRepository eventRepository, Config config) {
        FrameBuilder builder = new FrameBuilderResolver(config.eventType(), config.graphParameters(), false)
                .resolve();

        return new FlamegraphDataProvider(eventRepository, config, builder);
    }

    /**
     * Creates a new instance of the {@link FlamegraphDataProvider} for the differential mode. It automatically
     * resolves the {@link FrameBuilder} based on the event type and the graph parameters.
     * Then it starts processing the records from the event repository and builds the flamegraph.
     *
     * @param eventRepository repository to fetch all the records for processing
     * @param config          configuration for the flamegraph.
     * @return instance of the {@link FlamegraphDataProvider}.
     */
    public static FlamegraphDataProvider differential(ProfileEventRepository eventRepository, Config config) {
        FrameBuilder builder = new FrameBuilderResolver(config.eventType(), config.graphParameters(), true)
                .resolve();

        return new FlamegraphDataProvider(eventRepository, config, builder);
    }

    /**
     * Start consuming the records from the event repository and build the flamegraph.
     *
     * @return flamegraph data.
     */
    public FlamegraphData provide() {
        Frame frame = provideFrame();
        return flamegraphBuilder.build(frame);
    }

    /**
     * Start consuming the records from the event repository and build the flamegraph.
     *
     * @return flamegraph data.
     */
    public Frame provideFrame() {
        GraphParameters params = config.graphParameters();

        EventStreamConfigurer configurer = new EventStreamConfigurer()
                .withEventType(config.eventType())
                .withTimeRange(config.timeRange())
                .withIncludeFrames()
                .filterStacktraceTypes(params.stacktraceTypes())
                .filterStacktraceTags(params.stacktraceTags())
                .withThreads(params.threadMode())
                .withSpecifiedThread(config.threadInfo())
                .withWeight(params.useWeight());

        eventRepository.newEventStreamerFactory()
                .newFlamegraphStreamer(configurer)
                .startStreaming(frameBuilder::onRecord);

        Frame frame = frameBuilder.build();
        params.markers().forEach(frame::applyMarker);
        return frame;
    }

    private static FlameGraphBuilder resolveFlamegraphBuilder(Config config) {
        GraphParameters params = config.graphParameters();

        boolean withMarker = params.containsMarkers();
        if (config.eventType().isAllocationEvent()) {
            return FlameGraphBuilder.allocation(withMarker);
        } else if (config.eventType().isBlockingEvent()) {
            return FlameGraphBuilder.blocking(withMarker);
        } else {
            return FlameGraphBuilder.simple(withMarker);
        }
    }
}
