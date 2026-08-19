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

package cafe.jeffrey.flamegraph.api;

import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.profile.common.config.GraphComponents;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.flamegraph.GraphGenerator;
import cafe.jeffrey.flamegraph.ai.AiExportConfig;
import cafe.jeffrey.flamegraph.ai.FlamegraphAiMarkdownBuilder;
import cafe.jeffrey.flamegraph.proto.TimeseriesPoint;
import cafe.jeffrey.flamegraph.proto.TimeseriesSeries;
import cafe.jeffrey.flamegraph.provider.FlamegraphDataProvider;
import cafe.jeffrey.flamegraph.provider.TimeseriesDataProvider;
import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.jfr.events.trace.Tracer;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DbBasedFlamegraphGenerator implements GraphGenerator {

    private static final String SPAN_FLAMEGRAPH_BRANCH = "flamegraph.branch";
    private static final String SPAN_TIMESERIES_BRANCH = "timeseries.branch";
    private static final String SPAN_MARSHALLING = "graph.marshalling";


    private final ProfileEventStreamRepository eventRepository;
    private final double minFrameThresholdPct;
    private final AiExportConfig aiExportConfig;

    public DbBasedFlamegraphGenerator(
            ProfileEventStreamRepository eventRepository,
            double minFrameThresholdPct,
            AiExportConfig aiExportConfig) {
        this.eventRepository = eventRepository;
        this.minFrameThresholdPct = minFrameThresholdPct;
        this.aiExportConfig = aiExportConfig;
    }

    @Override
    public byte[] generate(GraphParameters params) {
        // Both branches run on a shared pool, where ScopedValue does not reach. fork captures the
        // enclosing span here and re-establishes it inside each task so the two halves of a graph
        // request stay under the request that asked for them instead of starting traces of their own.
        CompletableFuture<cafe.jeffrey.flamegraph.proto.FlamegraphData> flameFuture;
        if (GraphComponents.isFlamegraphCompatible(params.graphComponents())) {
            FlamegraphDataProvider flamegraphProvider = FlamegraphDataProvider.primary(eventRepository, params);
            flameFuture = CompletableFuture.supplyAsync(
                    Tracer.fork(SPAN_FLAMEGRAPH_BRANCH,
                            () -> flamegraphProvider.provideProto(minFrameThresholdPct)),
                    Schedulers.sharedParallel());
        } else {
            flameFuture = CompletableFuture.completedFuture(null);
        }

        CompletableFuture<TimeseriesData> timeseriesFuture;
        if (GraphComponents.isTimeseriesCompatible(params.graphComponents())) {
            TimeseriesDataProvider timeseriesProvider = new TimeseriesDataProvider(eventRepository, params);
            timeseriesFuture = CompletableFuture.supplyAsync(
                    Tracer.fork(SPAN_TIMESERIES_BRANCH,
                            timeseriesProvider::provide),
                    Schedulers.sharedParallel());
        } else {
            timeseriesFuture = CompletableFuture.completedFuture(null);
        }

        CompletableFuture.allOf(flameFuture, timeseriesFuture).join();

        return Tracer.call(SPAN_MARSHALLING, () -> marshal(flameFuture, timeseriesFuture));
    }

    private byte[] marshal(
            CompletableFuture<cafe.jeffrey.flamegraph.proto.FlamegraphData> flameFuture,
            CompletableFuture<TimeseriesData> timeseriesFuture) {

        cafe.jeffrey.flamegraph.proto.GraphData.Builder graphBuilder = cafe.jeffrey.flamegraph.proto.GraphData.newBuilder();

        cafe.jeffrey.flamegraph.proto.FlamegraphData flamegraphData = flameFuture.join();
        if (flamegraphData != null) {
            graphBuilder.setFlamegraph(flamegraphData);
        }

        TimeseriesData timeseriesData = timeseriesFuture.join();
        if (timeseriesData != null) {
            graphBuilder.setTimeseries(convertTimeseries(timeseriesData));
        }

        return graphBuilder.build().toByteArray();
    }

    /**
     * Generate an AI-friendly Markdown export of the flamegraph. Walks the
     * unpruned IR (visualization prune is skipped — AI export applies its
     * own threshold from {@link #aiExportConfig} so the LLM payload stays
     * compact). Threshold is set at bean construction from
     * {@code jeffrey.microscope.ai-export.flamegraph.min-frame-threshold-pct}.
     */
    public String generateAiExport(GraphParameters params) {
        return generateAiExportWithFrames(params).markdown();
    }

    /**
     * The AI export together with the call tree it was rendered from. Callers that need to reason about
     * the same frames the model was shown — grounding a cited frame, grading severity, diffing two
     * profiles — get them here instead of walking the IR a second time, which would risk describing a
     * slightly different tree than the one in the prompt.
     */
    public AiExport generateAiExportWithFrames(GraphParameters params) {
        Frame root = FlamegraphDataProvider.primary(eventRepository, params)
                .provideFrame();
        String markdown = new FlamegraphAiMarkdownBuilder(params.eventType(), aiExportConfig)
                .withThreadMode(params.threadMode())
                .build(root);
        return new AiExport(markdown, root);
    }

    /**
     * The rendered prompt and the unpruned call tree behind it.
     *
     * @param markdown the AI-friendly Markdown export
     * @param root     the frame tree the markdown was built from
     */
    public record AiExport(String markdown, Frame root) {
    }

    private static cafe.jeffrey.flamegraph.proto.TimeseriesData convertTimeseries(TimeseriesData data) {
        cafe.jeffrey.flamegraph.proto.TimeseriesData.Builder builder =
                cafe.jeffrey.flamegraph.proto.TimeseriesData.newBuilder();

        for (SingleSerie serie : data.series()) {
            TimeseriesSeries.Builder seriesBuilder = TimeseriesSeries.newBuilder()
                    .setName(serie.name());

            for (List<Long> point : serie.data()) {
                seriesBuilder.addData(TimeseriesPoint.newBuilder()
                        .setTimestamp(point.get(0))
                        .setValue(point.get(1))
                        .build());
            }

            builder.addSeries(seriesBuilder);
        }

        return builder.build();
    }
}
