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

package pbouda.jeffrey.generator.timeseries.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.Schedulers;
import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.frameir.processor.filter.EventProcessorFilters;
import pbouda.jeffrey.generator.timeseries.AbstractTimeseriesGenerator;
import pbouda.jeffrey.generator.timeseries.SimpleTimeseriesEventProcessor;
import pbouda.jeffrey.generator.timeseries.collector.TimeseriesCollector;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DiffTimeseriesGenerator extends AbstractTimeseriesGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ActiveSettingsProvider primarySettingsProvider;
    private final ActiveSettingsProvider secondarySettingsProvider;

    public DiffTimeseriesGenerator(
            ActiveSettingsProvider primarySettingsProvider,
            ActiveSettingsProvider secondarySettingsProvider) {

        this.primarySettingsProvider = primarySettingsProvider;
        this.secondarySettingsProvider = secondarySettingsProvider;
    }

    @Override
    public ArrayNode generate(Config config) {
        return generate(config, List.of());
    }

    @Override
    public ArrayNode generate(Config config, List<Marker> markers) {
        Function<RecordedEvent, Long> primaryExtractor = valueExtractor(config, primarySettingsProvider);
        Function<RecordedEvent, Long> secondaryExtractor = valueExtractor(config, secondarySettingsProvider);

        return differentialProcessing(config, primaryExtractor, secondaryExtractor);
    }

    private static ArrayNode differentialProcessing(
            Config config,
            Function<RecordedEvent, Long> primaryExtractor,
            Function<RecordedEvent, Long> secondaryExtractor) {

        var primaryProcessor = new SimpleTimeseriesEventProcessor(
                config.eventType(),
                primaryExtractor,
                config.timeRange(),
                EventProcessorFilters.excludeNonJavaAndIdleSamplesWithCaching(config.excludeNonJavaSamples(), config.excludeIdleSamples()));
        var secondaryProcessor = new SimpleTimeseriesEventProcessor(
                config.eventType(),
                secondaryExtractor,
                config.timeRange(),
                config.timeShift(),
                EventProcessorFilters.excludeNonJavaAndIdleSamplesWithCaching(config.excludeNonJavaSamples(), config.excludeIdleSamples()));

        CompletableFuture<ArrayNode> primaryFuture = CompletableFuture.supplyAsync(() -> {
            return JdkRecordingIterators.automaticAndCollect(
                    config.primaryRecordings(),
                    () -> primaryProcessor,
                    new TimeseriesCollector(config.primaryStartEnd()));
        }, Schedulers.parallel());

        CompletableFuture<ArrayNode> secondaryFuture = CompletableFuture.supplyAsync(() -> {
            return JdkRecordingIterators.automaticAndCollect(
                    config.secondaryRecordings(),
                    () -> secondaryProcessor,
                    new TimeseriesCollector(config.secondaryStartEnd()));
        }, Schedulers.parallel());

        CompletableFuture.allOf(primaryFuture, secondaryFuture).join();

        ObjectNode primary = MAPPER.createObjectNode()
                .put("name", "Primary Samples")
                .set("data", primaryFuture.join());
        ObjectNode secondary = MAPPER.createObjectNode()
                .put("name", "Secondary Samples")
                .set("data", secondaryFuture.join());

        return MAPPER.createArrayNode()
                .add(primary)
                .add(secondary);
    }
}
