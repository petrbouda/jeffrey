/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.flamegraph.diff;

import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.profile.common.config.GraphComponents;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.flamegraph.GraphGenerator;
import cafe.jeffrey.flamegraph.proto.TimeseriesPoint;
import cafe.jeffrey.flamegraph.proto.TimeseriesSeries;
import cafe.jeffrey.flamegraph.provider.FlamegraphDataProvider;
import cafe.jeffrey.flamegraph.provider.TimeseriesDataProvider;
import cafe.jeffrey.frameir.DiffFrame;
import cafe.jeffrey.frameir.DiffTreeGenerator;
import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DbBasedDiffgraphGenerator implements GraphGenerator {

    private final ProfileEventStreamRepository primaryRepository;
    private final ProfileEventStreamRepository secondaryRepository;
    private final double minFrameThresholdPct;

    public DbBasedDiffgraphGenerator(
            ProfileEventStreamRepository primaryRepository,
            ProfileEventStreamRepository secondaryRepository,
            double minFrameThresholdPct) {

        this.primaryRepository = primaryRepository;
        this.secondaryRepository = secondaryRepository;
        this.minFrameThresholdPct = minFrameThresholdPct;
    }

    @Override
    public byte[] generate(GraphParameters params) {
        /*
         * Asynchronously fetches the primary and secondary flamegraphs.
         */
        CompletableFuture<cafe.jeffrey.flamegraph.proto.FlamegraphData> flameFuture;
        if (GraphComponents.isFlamegraphCompatible(params.graphComponents())) {
            flameFuture = diffFrameAsync(params).thenApply(differentialFrames ->
                    new DiffgraphProtoFormatter(
                            differentialFrames, minFrameThresholdPct, params.useWeight()).format());
        } else {
            flameFuture = CompletableFuture.completedFuture(null);
        }

        /*
         * Asynchronously fetches the primary and secondary timeseries.
         */
        CompletableFuture<TimeseriesData> timeFuture;
        if (GraphComponents.isTimeseriesCompatible(params.graphComponents())) {
            TimeseriesDataProvider primaryTime = new TimeseriesDataProvider(primaryRepository, params);
            TimeseriesDataProvider secondaryTime = new TimeseriesDataProvider(secondaryRepository, params);

            CompletableFuture<TimeseriesData> primaryTimeFuture = CompletableFuture.supplyAsync(
                    primaryTime::provide, Schedulers.sharedParallel());
            CompletableFuture<TimeseriesData> secondaryTimeFuture = CompletableFuture.supplyAsync(
                    secondaryTime::provide, Schedulers.sharedParallel());

            timeFuture = primaryTimeFuture.thenCombine(secondaryTimeFuture, TimeseriesUtils::differential);
        } else {
            timeFuture = CompletableFuture.completedFuture(null);
        }

        CompletableFuture.allOf(flameFuture, timeFuture).join();

        cafe.jeffrey.flamegraph.proto.GraphData.Builder graphBuilder = cafe.jeffrey.flamegraph.proto.GraphData.newBuilder();

        cafe.jeffrey.flamegraph.proto.FlamegraphData flamegraphData = flameFuture.join();
        if (flamegraphData != null) {
            graphBuilder.setFlamegraph(flamegraphData);
        }

        TimeseriesData timeseriesData = timeFuture.join();
        if (timeseriesData != null) {
            graphBuilder.setTimeseries(convertTimeseries(timeseriesData));
        }

        return graphBuilder.build().toByteArray();
    }

    /**
     * The merged call tree of the two profiles, as the intermediate representation rather than as the
     * protobuf the browser draws.
     * <p>
     * The AI-facing exports read this: they rank and prune by <em>movement</em>, which the protobuf
     * has already thrown away in favour of what a renderer needs. Same tree either way, so the two
     * views of one comparison cannot disagree.
     */
    public DiffFrame diffFrame(GraphParameters params) {
        return diffFrameAsync(params).join();
    }

    private CompletableFuture<DiffFrame> diffFrameAsync(GraphParameters params) {
        FlamegraphDataProvider primaryFlame = FlamegraphDataProvider.differential(primaryRepository, params);
        FlamegraphDataProvider secondaryFlame = FlamegraphDataProvider.differential(secondaryRepository, params);

        CompletableFuture<Frame> primaryFlameFuture = CompletableFuture.supplyAsync(
                primaryFlame::provideFrame, Schedulers.sharedParallel());
        CompletableFuture<Frame> secondaryFlameFuture = CompletableFuture.supplyAsync(
                secondaryFlame::provideFrame, Schedulers.sharedParallel());

        return primaryFlameFuture.thenCombine(secondaryFlameFuture,
                (primary, secondary) -> new DiffTreeGenerator(primary, secondary).generate());
    }

    private static cafe.jeffrey.flamegraph.proto.TimeseriesData convertTimeseries(TimeseriesData data) {
        cafe.jeffrey.flamegraph.proto.TimeseriesData.Builder builder = cafe.jeffrey.flamegraph.proto.TimeseriesData.newBuilder();

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
