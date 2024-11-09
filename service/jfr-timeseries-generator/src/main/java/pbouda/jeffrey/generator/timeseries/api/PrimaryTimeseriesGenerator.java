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
import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.frameir.processor.filter.EventProcessorFilters;
import pbouda.jeffrey.generator.timeseries.*;
import pbouda.jeffrey.generator.timeseries.collector.SplitTimeseriesCollector;
import pbouda.jeffrey.generator.timeseries.collector.TimeseriesCollector;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public class PrimaryTimeseriesGenerator extends AbstractTimeseriesGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ActiveSettingsProvider settingsProvider;

    public PrimaryTimeseriesGenerator(ActiveSettingsProvider settingsProvider) {
        this.settingsProvider = settingsProvider;
    }

    @Override
    public ArrayNode generate(Config config) {
        return generate(config, List.of());
    }

    @Override
    public ArrayNode generate(Config config, List<Marker> markers) {
        Function<RecordedEvent, Long> valueExtractor = valueExtractor(config, settingsProvider);

        if (config.searchPattern() != null) {
            return primaryProcessingWithSearch(config, valueExtractor);
        } else if (!markers.isEmpty()) {
            return primaryProcessingWithPathMatching(config, markers, valueExtractor);
        } else {
            return primaryProcessing(config, valueExtractor);
        }
    }

    private static ArrayNode primaryProcessing(Config config, Function<RecordedEvent, Long> valueExtractor) {
        var primaryProcessor = new SimpleTimeseriesEventProcessor(
                config.eventType(),
                valueExtractor,
                config.timeRange(),
                EventProcessorFilters.excludeNonJavaAndIdleSamplesWithCaching(config.excludeNonJavaSamples(), config.excludeIdleSamples())
        );

        var samples = JdkRecordingIterators.automaticAndCollect(
                config.primaryRecordings(),
                () -> primaryProcessor,
                new TimeseriesCollector());

        ObjectNode primary = MAPPER.createObjectNode()
                .put("name", "Samples")
                .set("data", samples);

        return MAPPER.createArrayNode()
                .add(primary);
    }

    private static ArrayNode primaryProcessingWithPathMatching(
            Config config, List<Marker> markers, Function<RecordedEvent, Long> valueExtractor) {
        var processor = new PathMatchingTimeseriesEventProcessor(
                config.eventType(),
                valueExtractor,
                config.timeRange(),
                EventProcessorFilters.excludeNonJavaAndIdleSamplesWithCaching(config.excludeNonJavaSamples(), config.excludeIdleSamples()),
                markers);

        return splitTimeseries(config.primaryRecordings(), processor);
    }

    private static ArrayNode primaryProcessingWithSearch(
            Config config, Function<RecordedEvent, Long> valueExtractor) {
        var processor = new SearchableTimeseriesEventProcessor(
                config.eventType(),
                valueExtractor,
                config.timeRange(),
                EventProcessorFilters.excludeNonJavaAndIdleSamplesWithCaching(config.excludeNonJavaSamples(), config.excludeIdleSamples()),
                config.searchPattern()
        );

        return splitTimeseries(config.primaryRecordings(), processor);
    }

    private static ArrayNode splitTimeseries(
            List<Path> recordings, SplitTimeseriesEventProcessor processor) {

        var result = JdkRecordingIterators.automaticAndCollect(
                recordings, () -> processor, new SplitTimeseriesCollector());

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
