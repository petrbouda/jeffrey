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
import cafe.jeffrey.provider.profile.repository.ProfileEventStreamRepository;
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
            FlamegraphDataProvider primaryFlame = FlamegraphDataProvider.differential(primaryRepository, params, minFrameThresholdPct);
            FlamegraphDataProvider secondaryFlame = FlamegraphDataProvider.differential(secondaryRepository, params, minFrameThresholdPct);

            CompletableFuture<Frame> primaryFlameFuture = CompletableFuture.supplyAsync(
                    primaryFlame::provideFrame, Schedulers.sharedParallel());
            CompletableFuture<Frame> secondaryFlameFuture = CompletableFuture.supplyAsync(
                    secondaryFlame::provideFrame, Schedulers.sharedParallel());

            flameFuture = primaryFlameFuture.thenCombine(secondaryFlameFuture, (primary, secondary) -> {
                DiffFrame differentialFrames = new DiffTreeGenerator(primary, secondary).generate();
                return new DiffgraphProtoFormatter(differentialFrames, minFrameThresholdPct).format();
            });
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
