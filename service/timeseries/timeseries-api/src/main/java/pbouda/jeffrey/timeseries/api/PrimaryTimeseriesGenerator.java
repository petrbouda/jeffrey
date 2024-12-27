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
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.frameir.processor.filter.EventProcessorFilters;
import pbouda.jeffrey.jfrparser.api.EventProcessor;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;
import pbouda.jeffrey.timeseries.PathMatchingTimeseriesEventProcessor;
import pbouda.jeffrey.timeseries.SearchableTimeseriesEventProcessor;
import pbouda.jeffrey.timeseries.SimpleTimeseriesEventProcessor;
import pbouda.jeffrey.timeseries.SplitTimeseriesEventProcessor;
import pbouda.jeffrey.timeseries.collector.SplitTimeseriesCollector;
import pbouda.jeffrey.timeseries.collector.TimeseriesCollector;
import pbouda.jeffrey.timeseries.iterator.EventProcessingIterator;

import java.util.List;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

public class PrimaryTimeseriesGenerator extends AbstractTimeseriesGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ActiveSettingsProvider settingsProvider;

    public PrimaryTimeseriesGenerator(ActiveSettingsProvider settingsProvider) {
        this.settingsProvider = settingsProvider;
    }

    @Override
    public ArrayNode generate(EventProcessingIterator.Factory iterator, Config config) {
        return generate(iterator, config, List.of());
    }

    @Override
    public ArrayNode generate(EventProcessingIterator.Factory iteratorFactory, Config config, List<Marker> markers) {
        ToLongFunction<RecordedEvent> valueExtractor = valueExtractor(config, settingsProvider);
        EventProcessingIterator iterator = iteratorFactory.apply(config.primaryRecordings());

        if (config.graphParameters().searchPattern() != null) {
            return primaryProcessingWithSearch(iterator, config, valueExtractor);
        } else if (!markers.isEmpty()) {
            return primaryProcessingWithPathMatching(iterator, config, markers, valueExtractor);
        } else {
            return primaryProcessing(iterator, config, valueExtractor);
        }
    }

    private ArrayNode primaryProcessing(
            EventProcessingIterator iterator, Config config, ToLongFunction<RecordedEvent> valueExtractor) {

        Supplier<EventProcessor<LongLongHashMap>> primaryProcessor = () -> new SimpleTimeseriesEventProcessor(
                config.eventType(),
                valueExtractor,
                config.timeRange(),
                EventProcessorFilters.resolveFilters(config)
        );

        var samples = iterator.iterate(primaryProcessor, new TimeseriesCollector(config.primaryStartEnd()));

        ObjectNode primary = MAPPER.createObjectNode()
                .put("name", "Samples")
                .set("data", samples);

        return MAPPER.createArrayNode()
                .add(primary);
    }

    private ArrayNode primaryProcessingWithPathMatching(
            EventProcessingIterator iterator,
            Config config, List<Marker> markers,
            ToLongFunction<RecordedEvent> valueExtractor) {

        Supplier<SplitTimeseriesEventProcessor> processor = () -> new PathMatchingTimeseriesEventProcessor(
                config.eventType(),
                valueExtractor,
                config.timeRange(),
                EventProcessorFilters.resolveFilters(config),
                markers);

        return splitTimeseries(iterator, processor);
    }

    private ArrayNode primaryProcessingWithSearch(
            EventProcessingIterator iterator,
            Config config,
            ToLongFunction<RecordedEvent> valueExtractor) {

        Supplier<SplitTimeseriesEventProcessor> processor = () -> new SearchableTimeseriesEventProcessor(
                config.eventType(),
                valueExtractor,
                config.timeRange(),
                EventProcessorFilters.resolveFilters(config),
                config.graphParameters().searchPattern()
        );

        return splitTimeseries(iterator, processor);
    }

    private ArrayNode splitTimeseries(
            EventProcessingIterator iterator, Supplier<SplitTimeseriesEventProcessor> processorSupplier) {

        var result = iterator.iterate(processorSupplier, new SplitTimeseriesCollector());

        ObjectNode primary = MAPPER.createObjectNode()
                .put("name", "Samples")
                .set("data", result.get(0));

        ObjectNode primaryMatched = MAPPER.createObjectNode()
                .put("name", "Matched Samples")
                .set("data", result.get(1));

        return MAPPER.createArrayNode()
                .add(primary)
                .add(primaryMatched);
    }
}
