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

package pbouda.jeffrey.flamegraph.diff;

import pbouda.jeffrey.common.Schedulers;
import pbouda.jeffrey.common.config.GraphComponents;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.flamegraph.api.FlamegraphData;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.flamegraph.provider.FlamegraphDataProvider;
import pbouda.jeffrey.flamegraph.provider.TimeseriesDataProvider;
import pbouda.jeffrey.frameir.DiffFrame;
import pbouda.jeffrey.frameir.DiffTreeGenerator;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.provider.api.repository.ProfileEventStreamRepository;
import pbouda.jeffrey.timeseries.TimeseriesData;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.util.concurrent.CompletableFuture;

public class DbBasedDiffgraphGenerator implements GraphGenerator {

    private final ProfileEventStreamRepository primaryRepository;
    private final ProfileEventStreamRepository secondaryRepository;

    public DbBasedDiffgraphGenerator(
            ProfileEventStreamRepository primaryRepository,
            ProfileEventStreamRepository secondaryRepository) {

        this.primaryRepository = primaryRepository;
        this.secondaryRepository = secondaryRepository;
    }

    @Override
    public GraphData generate(GraphParameters params) {
        /*
         * Asynchronously fetches the primary and secondary flamegraphs.
         */
        CompletableFuture<FlamegraphData> flameFuture;
        if (GraphComponents.isFlamegraphCompatible(params.graphComponents())) {
            FlamegraphDataProvider primaryFlame = FlamegraphDataProvider.differential(primaryRepository, params);
            FlamegraphDataProvider secondaryFlame = FlamegraphDataProvider.differential(secondaryRepository, params);

            CompletableFuture<Frame> primaryFlameFuture = CompletableFuture.supplyAsync(
                    primaryFlame::provideFrame, Schedulers.sharedParallel());
            CompletableFuture<Frame> secondaryFlameFuture = CompletableFuture.supplyAsync(
                    secondaryFlame::provideFrame, Schedulers.sharedParallel());

            flameFuture = primaryFlameFuture.thenCombine(secondaryFlameFuture, (primary, secondary) -> {
                DiffFrame differentialFrames = new DiffTreeGenerator(primary, secondary).generate();
                return new DiffgraphFormatter(differentialFrames).format();
            });
        } else {
            flameFuture = CompletableFuture.completedFuture(FlamegraphData.empty());
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
            timeFuture = CompletableFuture.completedFuture(TimeseriesData.empty());
        }

        CompletableFuture.allOf(flameFuture, timeFuture).join();
        return new GraphData(flameFuture.join(), timeFuture.join());
    }
}
