package pbouda.jeffrey.generator.heatmap.api;

import pbouda.jeffrey.generator.heatmap.D3HeatmapEventProcessor;
import pbouda.jeffrey.generator.heatmap.RecordingFileIterator;
import pbouda.jeffrey.generator.heatmap.VMStartTimeProcessor;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Generate a data-file for the specific D3 heatmap from a selected event from JFR file.
 */
public class D3HeatmapGenerator implements HeatmapGenerator {

    @Override
    public void generate(Path jfrFile, OutputStream output, String eventName) {
        _generate(jfrFile, output, eventName, null);
    }

    @Override
    public byte[] generate(Path jfrFile, String eventName) {
        var baos = new ByteArrayOutputStream();
        _generate(jfrFile, baos, eventName, null);
        return baos.toByteArray();
    }

    @Override
    public byte[] generate(Path jfrFile, String eventName, Duration fromBeginning) {
        var baos = new ByteArrayOutputStream();
        _generate(jfrFile, baos, eventName, fromBeginning);
        return baos.toByteArray();
    }

    private static void _generate(Path jfrFile, OutputStream output, String eventName, Duration fromBeginning) {
        VMStartTimeProcessor vmStartTimeProcessor = new VMStartTimeProcessor();

        RecordingFileIterator iterator = new RecordingFileIterator(jfrFile);
        iterator.iterate(vmStartTimeProcessor);

        D3HeatmapEventProcessor processor = new D3HeatmapEventProcessor(
                eventName,
                vmStartTimeProcessor.startTime(),
                output,
                fromBeginning);

        iterator.iterate(processor);
    }
}
