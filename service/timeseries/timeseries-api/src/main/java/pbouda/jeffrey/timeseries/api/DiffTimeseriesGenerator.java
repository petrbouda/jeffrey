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

package pbouda.jeffrey.timeseries.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.ProfilingStartEnd;
import pbouda.jeffrey.common.Schedulers;
import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.frameir.processor.filter.EventProcessorFilters;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;
import pbouda.jeffrey.timeseries.SimpleTimeseriesEventProcessor;
import pbouda.jeffrey.timeseries.collector.TimeseriesCollector;
import pbouda.jeffrey.timeseries.iterator.EventProcessingIterator;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToLongFunction;

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
    public ArrayNode generate(EventProcessingIterator.Factory iteratorFactory, Config config) {
        return generate(iteratorFactory, config, List.of());
    }

    @Override
    public ArrayNode generate(EventProcessingIterator.Factory iteratorFactory, Config config, List<Marker> markers) {
        ToLongFunction<RecordedEvent> primaryExtractor = valueExtractor(config, primarySettingsProvider);
        ToLongFunction<RecordedEvent> secondaryExtractor = valueExtractor(config, secondarySettingsProvider);

        return differentialProcessing(iteratorFactory, config, primaryExtractor, secondaryExtractor);
    }

    private static ArrayNode differentialProcessing(
            EventProcessingIterator.Factory iteratorFactory,
            Config config,
            ToLongFunction<RecordedEvent> primaryExtractor,
            ToLongFunction<RecordedEvent> secondaryExtractor) {

        var primaryProcessor = new SimpleTimeseriesEventProcessor(
                config.eventType(),
                primaryExtractor,
                config.timeRange(),
                EventProcessorFilters.resolveFilters(config));
        var secondaryProcessor = new SimpleTimeseriesEventProcessor(
                config.eventType(),
                secondaryExtractor,
                config.timeRange(),
                config.timeShift(),
                EventProcessorFilters.resolveFilters(config));

        CompletableFuture<ArrayNode> primaryFuture = CompletableFuture.supplyAsync(() -> {
            return iteratorFactory.apply(config.primaryRecordings())
                    .iterate(() -> primaryProcessor, new TimeseriesCollector(config.primaryStartEnd()));
        }, Schedulers.parallel());

        CompletableFuture<ArrayNode> secondaryFuture = CompletableFuture.supplyAsync(() -> {
            return iteratorFactory.apply(config.secondaryRecordings())
                    .iterate(() -> secondaryProcessor, new TimeseriesCollector(calculateSecondaryStartEnd(config)));
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

    /**
     * An interval for visualization Primary and Secondary recordings.
     *
     * @param config configuration
     * @return interval for timeseries visualization
     */
    private static ProfilingStartEnd calculateSecondaryStartEnd(Config config) {
        return new ProfilingStartEnd(
                config.primaryStartEnd().start(),
                config.secondaryStartEnd().end().minus(config.timeShift())
        );
    }
}
