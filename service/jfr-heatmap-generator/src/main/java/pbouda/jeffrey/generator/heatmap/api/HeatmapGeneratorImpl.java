package pbouda.jeffrey.generator.heatmap.api;

import pbouda.jeffrey.generator.heatmap.HeatmapEventProcessor;
import pbouda.jeffrey.generator.heatmap.HeatmapConfig;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

import java.io.ByteArrayOutputStream;

public class HeatmapGeneratorImpl implements HeatmapGenerator {

    @Override
    public byte[] generate(HeatmapConfig config) {
        var output = new ByteArrayOutputStream();
        var iterator = new RecordingFileIterator<>(config.recording());
        iterator.iterate(new HeatmapEventProcessor(config, output));
        return output.toByteArray();
    }
}
