package pbouda.jeffrey.generator.timeseries.api;

import com.fasterxml.jackson.databind.node.ArrayNode;
import pbouda.jeffrey.generator.timeseries.TimeseriesConfig;
import pbouda.jeffrey.generator.timeseries.TimeseriesEventProcessor;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

public class TimeseriesGeneratorImpl implements TimeseriesGenerator {

    @Override
    public ArrayNode generate(TimeseriesConfig config) {
        TimeseriesEventProcessor processor = new TimeseriesEventProcessor(config);
        return new RecordingFileIterator<>(config.recording(), processor)
                .collect();
    }
}
