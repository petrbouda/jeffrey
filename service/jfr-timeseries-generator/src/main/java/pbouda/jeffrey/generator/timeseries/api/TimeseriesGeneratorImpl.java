package pbouda.jeffrey.generator.timeseries.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.generator.timeseries.SearchableTimeseriesEventProcessor;
import pbouda.jeffrey.generator.timeseries.TimeseriesEventProcessor;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

public class TimeseriesGeneratorImpl implements TimeseriesGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ArrayNode generate(Config config) {
        if (config.type() == Config.Type.PRIMARY) {
            if (config.searchPattern() == null) {
                return primaryProcessing(config);
            } else {
                return primaryProcessingWithSearch(config);
            }
        } else {
            return differentialProcessing(config);
        }
    }

    private static ArrayNode primaryProcessing(Config config) {
        TimeseriesEventProcessor primaryProcessor = new TimeseriesEventProcessor(
                config.eventType(), config.primaryTimeRange());

        var samples = new RecordingFileIterator<>(config.primaryRecording(), primaryProcessor)
                .collect();

        ObjectNode primary = MAPPER.createObjectNode()
                .put("name", "Samples")
                .set("data", samples);

        return MAPPER.createArrayNode()
                .add(primary);
    }

    private static ArrayNode primaryProcessingWithSearch(Config config) {
        var primaryProcessor = new SearchableTimeseriesEventProcessor(
                config.eventType(), config.primaryTimeRange(), config.searchPattern());

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

    private static ArrayNode differentialProcessing(Config config) {
        // We need to correlate start-time of the primary and secondary profiles
        // Secondary profile will be moved in time to start at the same time as primary profile
        long timeShift = calculateTimeShift(config);

        TimeseriesEventProcessor primaryProcessor = new TimeseriesEventProcessor(
                config.eventType(), config.primaryTimeRange());
        TimeseriesEventProcessor secondaryProcessor = new TimeseriesEventProcessor(
                timeShift, config.eventType(), config.primaryTimeRange());

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
