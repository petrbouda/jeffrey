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
import pbouda.jeffrey.generator.timeseries.PathMatchingTimeseriesEventProcessor;
import pbouda.jeffrey.generator.timeseries.SearchableTimeseriesEventProcessor;
import pbouda.jeffrey.generator.timeseries.SimpleTimeseriesEventProcessor;
import pbouda.jeffrey.generator.timeseries.SplitTimeseriesEventProcessor;
import pbouda.jeffrey.generator.timeseries.collector.SplitTimeseriesCollector;
import pbouda.jeffrey.generator.timeseries.collector.TimeseriesCollector;
import pbouda.jeffrey.jfrparser.jdk.RecordingIterators;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class TimeseriesGeneratorImpl implements TimeseriesGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Function<RecordedEvent, Long> INCREMENTAL_VALUE_EXTRACTOR = __ -> 1L;

    @Override
    public ArrayNode generate(Config config) {
        return generate(config, List.of());
    }

    @Override
    public ArrayNode generate(Config config, List<Marker> markers) {
        var valueExtractor = INCREMENTAL_VALUE_EXTRACTOR;
        if (config.collectWeight()) {
            valueExtractor = config.eventType().weightExtractor();
            if (valueExtractor == null) {
                valueExtractor = INCREMENTAL_VALUE_EXTRACTOR;
            }
        }

        if (config.type() == Config.Type.PRIMARY) {
            if (config.searchPattern() != null) {
                return primaryProcessingWithSearch(config, valueExtractor);
            } else if (!markers.isEmpty()) {
                return primaryProcessingWithPathMatching(config, markers, valueExtractor);
            } else {
                return primaryProcessing(config, valueExtractor);
            }
        } else {
            return differentialProcessing(config, valueExtractor);
        }
    }

    private static ArrayNode primaryProcessing(Config config, Function<RecordedEvent, Long> valueExtractor) {
        var primaryProcessor = new SimpleTimeseriesEventProcessor(
                config.eventType(), valueExtractor, config.primaryTimeRange());

        var samples = RecordingIterators.automaticAndCollect(
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
                config.eventType(), valueExtractor, config.primaryTimeRange(), markers);

        return splitTimeseries(config.primaryRecordings(), processor);
    }

    private static ArrayNode primaryProcessingWithSearch(
            Config config, Function<RecordedEvent, Long> valueExtractor) {
        var processor = new SearchableTimeseriesEventProcessor(
                config.eventType(), valueExtractor, config.primaryTimeRange(), config.searchPattern());

        return splitTimeseries(config.primaryRecordings(), processor);
    }

    private static ArrayNode splitTimeseries(
            List<Path> recordings, SplitTimeseriesEventProcessor processor) {

        var result = RecordingIterators.automaticAndCollect(
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

    private static ArrayNode differentialProcessing(Config config, Function<RecordedEvent, Long> valueExtractor) {
        // We need to correlate start-time of the primary and secondary profiles
        // Secondary profile will be moved in time to start at the same time as primary profile
        long timeShift = calculateTimeShift(config);

        var primaryProcessor = new SimpleTimeseriesEventProcessor(
                config.eventType(), valueExtractor, config.primaryTimeRange());
        var secondaryProcessor = new SimpleTimeseriesEventProcessor(
                config.eventType(), valueExtractor, config.primaryTimeRange(), timeShift);

        CompletableFuture<ArrayNode> primaryFuture = CompletableFuture.supplyAsync(() -> {
            return RecordingIterators.automaticAndCollect(
                    config.primaryRecordings(),
                    () -> primaryProcessor,
                    new TimeseriesCollector());
        }, Schedulers.parallel());

        CompletableFuture<ArrayNode> secondaryFuture = CompletableFuture.supplyAsync(() -> {
            return RecordingIterators.automaticAndCollect(
                    config.secondaryRecordings(),
                    () -> secondaryProcessor,
                    new TimeseriesCollector());
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

    private static long calculateTimeShift(Config config) {
        long primary = config.primaryStart().toEpochMilli();
        long secondary = config.secondaryStart().toEpochMilli();
        return primary - secondary;
    }
}
