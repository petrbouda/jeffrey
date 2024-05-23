package pbouda.jeffrey.generator.timeseries.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.generator.timeseries.SearchableTimeseriesEventProcessor;
import pbouda.jeffrey.generator.timeseries.TimeseriesEventProcessor;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

import java.util.function.Function;

public class TimeseriesGeneratorImpl implements TimeseriesGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Function<RecordedEvent, Long> INCREMENTAL_VALUE_EXTRACTOR = _ -> 1L;

    @Override
    public ArrayNode generate(Config config) {
        var valueExtractor = INCREMENTAL_VALUE_EXTRACTOR;
        if (config.collectWeight()) {
            valueExtractor = e -> e.getLong(config.eventType().weightFieldName());
        }

        if (config.type() == Config.Type.PRIMARY) {
            if (config.searchPattern() == null) {
                return primaryProcessing(config, valueExtractor);
            } else {
                return primaryProcessingWithSearch(config, valueExtractor);
            }
        } else {
            return differentialProcessing(config, valueExtractor);
        }
    }

    private static ArrayNode primaryProcessing(Config config, Function<RecordedEvent, Long> valueExtractor) {
        TimeseriesEventProcessor primaryProcessor = new TimeseriesEventProcessor(
                config.eventType(), valueExtractor, config.primaryTimeRange());

        var samples = new RecordingFileIterator<>(config.primaryRecording(), primaryProcessor)
                .collect();

        ObjectNode primary = MAPPER.createObjectNode()
                .put("name", "Samples")
                .set("data", samples);

        return MAPPER.createArrayNode()
                .add(primary);
    }

    private static ArrayNode primaryProcessingWithSearch(Config config, Function<RecordedEvent, Long> valueExtractor) {
        var primaryProcessor = new SearchableTimeseriesEventProcessor(
                config.eventType(), valueExtractor, config.primaryTimeRange(), config.searchPattern());

        var result = new RecordingFileIterator<>(config.primaryRecording(), primaryProcessor)
                .collect();

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

        TimeseriesEventProcessor primaryProcessor = new TimeseriesEventProcessor(
                config.eventType(), valueExtractor, config.primaryTimeRange());
        TimeseriesEventProcessor secondaryProcessor = new TimeseriesEventProcessor(
                config.eventType(), valueExtractor, config.primaryTimeRange(), timeShift);

        var primaryData = new RecordingFileIterator<>(config.primaryRecording(), primaryProcessor)
                .collect();
        var secondaryData = new RecordingFileIterator<>(config.secondaryRecording(), secondaryProcessor)
                .collect();

        ObjectNode primary = MAPPER.createObjectNode()
                .put("name", "Primary Samples")
                .set("data", primaryData);
        ObjectNode secondary = MAPPER.createObjectNode()
                .put("name", "Secondary Samples")
                .set("data", secondaryData);

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
