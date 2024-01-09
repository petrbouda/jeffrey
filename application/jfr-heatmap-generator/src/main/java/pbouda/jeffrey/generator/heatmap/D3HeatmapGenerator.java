package pbouda.jeffrey.generator.heatmap;

import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Generate a data-file for the specific D3 heatmap from a selected event from JFR file.
 */
public class D3HeatmapGenerator implements HeatmapGenerator {

    @Override
    public void generate(Path jfrFile, OutputStream output, String eventName) {
        VMStartTimeProcessor vmStartTimeProcessor = new VMStartTimeProcessor();

        RecordingFileIterator iterator = new RecordingFileIterator(jfrFile);
        iterator.iterate(vmStartTimeProcessor);
        iterator.iterate(new D3HeatmapEventProcessor(eventName, vmStartTimeProcessor.startTime()));
    }
}
