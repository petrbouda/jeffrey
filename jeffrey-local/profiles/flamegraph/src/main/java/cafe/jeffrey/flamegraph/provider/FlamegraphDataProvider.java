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

package cafe.jeffrey.flamegraph.provider;

import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.flamegraph.FlameGraphProtoBuilder;
import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.frameir.FrameBuilder;
import cafe.jeffrey.frameir.FrameBuilderResolver;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;

public class FlamegraphDataProvider {

    private final ProfileEventStreamRepository eventStreamRepository;
    private final FrameBuilder frameBuilder;
    private final GraphParameters graphParameters;
    private final double minFrameThresholdPct;

    private FlamegraphDataProvider(
            ProfileEventStreamRepository eventStreamRepository,
            GraphParameters graphParameters,
            FrameBuilder frameBuilder,
            double minFrameThresholdPct) {

        this.eventStreamRepository = eventStreamRepository;
        this.graphParameters = graphParameters;
        this.frameBuilder = frameBuilder;
        this.minFrameThresholdPct = minFrameThresholdPct;
    }

    /**
     * Creates a new instance of the {@link FlamegraphDataProvider} for the primary mode. It automatically resolves the
     * {@link FrameBuilder} based on the event type and the graph parameters. Then it starts processing the records
     * from the event repository and builds the flamegraph.
     *
     * @param eventStreamRepository repository to fetch all the records for processing
     * @param params                configuration for the flamegraph.
     * @param minFrameThresholdPct  minimum frame threshold percentage for pruning low-relevancy frames.
     * @return instance of the {@link FlamegraphDataProvider}.
     */
    public static FlamegraphDataProvider primary(
            ProfileEventStreamRepository eventStreamRepository, GraphParameters params, double minFrameThresholdPct) {

        FrameBuilder builder = new FrameBuilderResolver(params, false)
                .resolve();

        return new FlamegraphDataProvider(eventStreamRepository, params, builder, minFrameThresholdPct);
    }

    /**
     * Creates a new instance of the {@link FlamegraphDataProvider} for the differential mode. It automatically
     * resolves the {@link FrameBuilder} based on the event type and the graph parameters.
     * Then it starts processing the records from the event repository and builds the flamegraph.
     *
     * @param eventStreamRepository repository to fetch all the records for processing
     * @param params                configuration for the flamegraph.
     * @param minFrameThresholdPct  minimum frame threshold percentage for pruning low-relevancy frames.
     * @return instance of the {@link FlamegraphDataProvider}.
     */
    public static FlamegraphDataProvider differential(
            ProfileEventStreamRepository eventStreamRepository, GraphParameters params, double minFrameThresholdPct) {

        FrameBuilder builder = new FrameBuilderResolver(params, true)
                .resolve();

        return new FlamegraphDataProvider(eventStreamRepository, params, builder, minFrameThresholdPct);
    }

    /**
     * Start consuming the records from the event repository and build the flamegraph in Protobuf format.
     *
     * @return flamegraph data in Protobuf format.
     */
    public cafe.jeffrey.flamegraph.proto.FlamegraphData provideProto() {
        Frame frame = provideFrame();
        FlameGraphProtoBuilder protoBuilder = resolveFlamegraphProtoBuilder(graphParameters, minFrameThresholdPct);
        return protoBuilder.build(frame);
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

    private static FlameGraphProtoBuilder resolveFlamegraphProtoBuilder(GraphParameters params, double minFrameThresholdPct) {
        boolean withMarker = params.containsMarkers();

        if (params.eventType().isAllocationEvent()) {
            return FlameGraphProtoBuilder.allocation(withMarker, minFrameThresholdPct);
        } else if (params.eventType().isBlockingEvent()) {
            return FlameGraphProtoBuilder.blocking(withMarker, minFrameThresholdPct);
        } else if (params.eventType().isMethodTraceEvent()) {
            return FlameGraphProtoBuilder.latency(withMarker, minFrameThresholdPct);
        } else {
            return FlameGraphProtoBuilder.simple(withMarker, minFrameThresholdPct);
        }
    }
}
