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

import pbouda.jeffrey.common.Schedulers;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.flamegraph.GraphGenerator;
import pbouda.jeffrey.flamegraph.provider.FlamegraphDataProvider;
import pbouda.jeffrey.flamegraph.provider.TimeseriesDataProvider;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.timeseries.TimeseriesData;

import java.util.concurrent.CompletableFuture;

public class DbBasedFlamegraphGenerator implements GraphGenerator {

    private final ProfileEventRepository eventRepository;

    public DbBasedFlamegraphGenerator(ProfileEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public GraphData generate(Config config) {
        FlamegraphDataProvider flamegraphProvider = FlamegraphDataProvider.primary(eventRepository, config);
        TimeseriesDataProvider timeseriesProvider = TimeseriesDataProvider.primary(eventRepository, config);

        CompletableFuture<FlamegraphData> flameFuture = CompletableFuture.supplyAsync(
                flamegraphProvider::provide, Schedulers.sharedParallel());

        CompletableFuture<TimeseriesData> timeseriesFuture = CompletableFuture.supplyAsync(
                timeseriesProvider::provide, Schedulers.sharedParallel());

        CompletableFuture.allOf(flameFuture, timeseriesFuture).join();

        return new GraphData(flameFuture.join(), timeseriesFuture.join());
    }
}
