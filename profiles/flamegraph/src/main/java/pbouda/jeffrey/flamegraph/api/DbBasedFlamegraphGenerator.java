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

package pbouda.jeffrey.flamegraph.api;

import pbouda.jeffrey.shared.common.Schedulers;
import pbouda.jeffrey.profile.common.config.GraphComponents;
import pbouda.jeffrey.profile.common.config.GraphParameters;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.flamegraph.proto.TimeseriesPoint;
import pbouda.jeffrey.flamegraph.proto.TimeseriesSeries;
import pbouda.jeffrey.flamegraph.provider.FlamegraphDataProvider;
import pbouda.jeffrey.flamegraph.provider.TimeseriesDataProvider;
import pbouda.jeffrey.provider.profile.repository.ProfileEventStreamRepository;
import pbouda.jeffrey.timeseries.SingleSerie;
import pbouda.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DbBasedFlamegraphGenerator implements GraphGenerator {

    private final ProfileEventStreamRepository eventRepository;

    public DbBasedFlamegraphGenerator(ProfileEventStreamRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public byte[] generate(GraphParameters params) {
        CompletableFuture<pbouda.jeffrey.flamegraph.proto.FlamegraphData> flameFuture;
        if (GraphComponents.isFlamegraphCompatible(params.graphComponents())) {
            FlamegraphDataProvider flamegraphProvider = FlamegraphDataProvider.primary(eventRepository, params);
            flameFuture = CompletableFuture.supplyAsync(flamegraphProvider::provideProto, Schedulers.sharedParallel());
        } else {
            flameFuture = CompletableFuture.completedFuture(null);
        }

        CompletableFuture<TimeseriesData> timeseriesFuture;
        if (GraphComponents.isTimeseriesCompatible(params.graphComponents())) {
            TimeseriesDataProvider timeseriesProvider = new TimeseriesDataProvider(eventRepository, params);
            timeseriesFuture = CompletableFuture.supplyAsync(timeseriesProvider::provide, Schedulers.sharedParallel());
        } else {
            timeseriesFuture = CompletableFuture.completedFuture(null);
        }

        CompletableFuture.allOf(flameFuture, timeseriesFuture).join();

        pbouda.jeffrey.flamegraph.proto.GraphData.Builder graphBuilder = pbouda.jeffrey.flamegraph.proto.GraphData.newBuilder();

        pbouda.jeffrey.flamegraph.proto.FlamegraphData flamegraphData = flameFuture.join();
        if (flamegraphData != null) {
            graphBuilder.setFlamegraph(flamegraphData);
        }

        TimeseriesData timeseriesData = timeseriesFuture.join();
        if (timeseriesData != null) {
            graphBuilder.setTimeseries(convertTimeseries(timeseriesData));
        }

        return graphBuilder.build().toByteArray();
    }

    private static pbouda.jeffrey.flamegraph.proto.TimeseriesData convertTimeseries(TimeseriesData data) {
        pbouda.jeffrey.flamegraph.proto.TimeseriesData.Builder builder = pbouda.jeffrey.flamegraph.proto.TimeseriesData.newBuilder();

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
