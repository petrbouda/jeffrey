package pbouda.jeffrey.generator.heatmap.api;

import pbouda.jeffrey.generator.heatmap.D3HeatmapEventProcessor;
import pbouda.jeffrey.generator.heatmap.HeatmapConfig;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

import java.io.ByteArrayOutputStream;

/**
 * Generate a data-file for the specific D3 heatmap from a selected event from JFR file.
 */
public class D3HeatmapGenerator implements HeatmapGenerator {

    @Override
    public byte[] generate(HeatmapConfig config) {
        var output = new ByteArrayOutputStream();
        var iterator = new RecordingFileIterator<>(config.jfrFile());
        iterator.iterate(new D3HeatmapEventProcessor(config, output));
        return output.toByteArray();
    }
}
