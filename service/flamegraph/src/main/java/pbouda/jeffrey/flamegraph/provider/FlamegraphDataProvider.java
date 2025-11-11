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

import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.flamegraph.FlameGraphBuilder;
import pbouda.jeffrey.flamegraph.api.FlamegraphData;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.FrameBuilder;
import pbouda.jeffrey.frameir.FrameBuilderResolver;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventStreamRepository;

public class FlamegraphDataProvider {

    private final ProfileEventStreamRepository eventStreamRepository;
    private final FrameBuilder frameBuilder;
    private final FlameGraphBuilder flamegraphBuilder;
    private final GraphParameters graphParameters;

    private FlamegraphDataProvider(
            ProfileEventStreamRepository eventStreamRepository, GraphParameters graphParameters, FrameBuilder frameBuilder) {

        this.eventStreamRepository = eventStreamRepository;
        this.graphParameters = graphParameters;
        this.frameBuilder = frameBuilder;
        this.flamegraphBuilder = resolveFlamegraphBuilder(graphParameters);
    }

    /**
     * Creates a new instance of the {@link FlamegraphDataProvider} for the primary mode. It automatically resolves the
     * {@link FrameBuilder} based on the event type and the graph parameters. Then it starts processing the records
     * from the event repository and builds the flamegraph.
     *
     * @param eventStreamRepository repository to fetch all the records for processing
     * @param params                configuration for the flamegraph.
     * @return instance of the {@link FlamegraphDataProvider}.
     */
    public static FlamegraphDataProvider primary(ProfileEventStreamRepository eventStreamRepository, GraphParameters params) {
        FrameBuilder builder = new FrameBuilderResolver(params, false)
                .resolve();

        return new FlamegraphDataProvider(eventStreamRepository, params, builder);
    }

    /**
     * Creates a new instance of the {@link FlamegraphDataProvider} for the differential mode. It automatically
     * resolves the {@link FrameBuilder} based on the event type and the graph parameters.
     * Then it starts processing the records from the event repository and builds the flamegraph.
     *
     * @param eventStreamRepository repository to fetch all the records for processing
     * @param params                configuration for the flamegraph.
     * @return instance of the {@link FlamegraphDataProvider}.
     */
    public static FlamegraphDataProvider differential(ProfileEventStreamRepository eventStreamRepository, GraphParameters params) {
        FrameBuilder builder = new FrameBuilderResolver(params, true)
                .resolve();

        return new FlamegraphDataProvider(eventStreamRepository, params, builder);
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
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(graphParameters.eventType())
                .withTimeRange(graphParameters.timeRange())
                .withIncludeFrames()
                .filterStacktraceTypes(graphParameters.stacktraceTypes())
                .filterStacktraceTags(graphParameters.stacktraceTags())
                .withThreads(graphParameters.threadMode())
                .withSpecifiedThread(graphParameters.threadInfo())
                .withWeight(graphParameters.useWeight());

        Frame frame = eventStreamRepository.flamegraphStreamer(configurer, frameBuilder);

        if (graphParameters.markers() != null) {
            graphParameters.markers().forEach(frame::applyMarker);
        }
        return frame;
    }

    private static FlameGraphBuilder resolveFlamegraphBuilder(GraphParameters params) {
        boolean withMarker = params.containsMarkers();

        if (params.eventType().isAllocationEvent()) {
            return FlameGraphBuilder.allocation(withMarker);
        } else if (params.eventType().isBlockingEvent()) {
            return FlameGraphBuilder.blocking(withMarker);
        } else if (params.eventType().isMethodTraceEvent()) {
            return FlameGraphBuilder.latency(withMarker);
        } else {
            return FlameGraphBuilder.simple(withMarker);
        }
    }
}
