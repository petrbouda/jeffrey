package pbouda.jeffrey.generator.timeseries.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.generator.timeseries.TimeseriesConfig;
import pbouda.jeffrey.generator.timeseries.TimeseriesConfig.Type;
import pbouda.jeffrey.generator.timeseries.TimeseriesEventProcessor;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

public class TimeseriesGeneratorImpl implements TimeseriesGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ArrayNode generate(TimeseriesConfig config) {
        if (config.type() == Type.PRIMARY) {
            return primaryProcessing(config);
        } else {
            return differentialProcessing(config);
        }
    }

    private static ArrayNode primaryProcessing(TimeseriesConfig config) {
        TimeseriesEventProcessor primaryProcessor = new TimeseriesEventProcessor(
                config.eventType(), config.primaryStart(), config.start(), config.duration());

        var samples = new RecordingFileIterator<>(config.primaryRecording(), primaryProcessor)
                .collect();

        ObjectNode primary = MAPPER.createObjectNode()
                .put("name", "Samples")
                .set("data", samples);

        return MAPPER.createArrayNode()
                .add(primary);
    }

    private static ArrayNode differentialProcessing(TimeseriesConfig config) {
        // We need to correlate start-time of the primary and secondary profiles
        // Secondary profile will be moved in time to start at the same time as primary profile
        long timeShift = calculateTimeShift(config);

        TimeseriesEventProcessor primaryProcessor = new TimeseriesEventProcessor(
                config.eventType(), config.primaryStart(), config.start(), config.duration());
        TimeseriesEventProcessor secondaryProcessor = new TimeseriesEventProcessor(
                timeShift, config.eventType(), config.secondaryStart(), config.start(), config.duration());

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

    private static long calculateTimeShift(TimeseriesConfig config) {
        long primary = config.primaryStart().toEpochMilli();
        long secondary = config.secondaryStart().toEpochMilli();
        return primary - secondary;
    }
}
