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
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.flamegraph.api.FlamegraphData;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.flamegraph.provider.FlamegraphDataProvider;
import pbouda.jeffrey.flamegraph.provider.TimeseriesDataProvider;
import pbouda.jeffrey.frameir.DiffFrame;
import pbouda.jeffrey.frameir.DiffTreeGenerator;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.timeseries.TimeseriesData;
import pbouda.jeffrey.timeseries.TimeseriesUtils;

import java.util.concurrent.CompletableFuture;

public class DbBasedDiffgraphGenerator implements GraphGenerator {

    private final ProfileEventRepository primaryEventRepository;
    private final ProfileEventRepository secondaryEventRepository;

    public DbBasedDiffgraphGenerator(
            ProfileEventRepository primaryEventRepository,
            ProfileEventRepository secondaryEventRepository) {

        this.primaryEventRepository = primaryEventRepository;
        this.secondaryEventRepository = secondaryEventRepository;
    }

    @Override
    public GraphData generate(Config config) {
        FlamegraphDataProvider primaryFlame = FlamegraphDataProvider.differential(primaryEventRepository, config);
        TimeseriesDataProvider primaryTime = TimeseriesDataProvider.differential(primaryEventRepository, config);

        FlamegraphDataProvider secondaryFlame = FlamegraphDataProvider.differential(secondaryEventRepository, config);
        TimeseriesDataProvider secondaryTime = TimeseriesDataProvider.differential(secondaryEventRepository, config);

        /*
         * Asynchronously fetches the primary and secondary flamegraphs.
         */
        CompletableFuture<Frame> primaryFlameFuture = CompletableFuture.supplyAsync(
                primaryFlame::provideFrame, Schedulers.sharedParallel());
        CompletableFuture<Frame> secondaryFlameFuture = CompletableFuture.supplyAsync(
                secondaryFlame::provideFrame, Schedulers.sharedParallel());

        CompletableFuture<FlamegraphData> flamegraphFuture =
                primaryFlameFuture.thenCombine(secondaryFlameFuture, (primary, secondary) -> {
                    DiffFrame differentialFrames = new DiffTreeGenerator(primary, secondary).generate();
                    return new DiffgraphFormatter(differentialFrames).format();
                });

        /*
         * Asynchronously fetches the primary and secondary timeseries.
         */
        CompletableFuture<TimeseriesData> primaryTimeFuture = CompletableFuture.supplyAsync(
                primaryTime::provide, Schedulers.sharedParallel());
        CompletableFuture<TimeseriesData> secondaryTimeFuture = CompletableFuture.supplyAsync(
                secondaryTime::provide, Schedulers.sharedParallel());

        CompletableFuture<TimeseriesData> timeseriesFuture =
                primaryTimeFuture.thenCombine(secondaryTimeFuture, TimeseriesUtils::differential);

        CompletableFuture.allOf(flamegraphFuture, timeseriesFuture).join();
        return new GraphData(flamegraphFuture.join(), timeseriesFuture.join());
    }
}
