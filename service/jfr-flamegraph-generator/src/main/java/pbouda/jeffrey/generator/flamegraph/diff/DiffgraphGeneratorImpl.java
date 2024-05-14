package pbouda.jeffrey.generator.flamegraph.diff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.flamegraph.Frame;
import pbouda.jeffrey.generator.flamegraph.processor.ExecutionSampleEventProcessor;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;
import pbouda.jeffrey.generator.flamegraph.tree.FrameTreeBuilder;
import pbouda.jeffrey.generator.flamegraph.tree.SimpleFrameTreeBuilder;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

import java.nio.file.Path;
import java.util.List;

public class DiffgraphGeneratorImpl implements DiffgraphGenerator {

    @Override
    public ObjectNode generate(Config config) {
        // We need to correlate start-time of the primary and secondary profiles
        // Secondary profile will be moved in time to start at the same time as primary profile
        Frame comparison = _generate(config.primaryRecording(), config.eventType(), config.primaryTimeRange(), config);
        Frame baseline = _generate(config.secondaryRecording(), config.eventType(), config.secondaryTimeRange(), config);

        DiffTreeGenerator treeGenerator = new DiffTreeGenerator(baseline, comparison);
        DiffFrame diffFrame = treeGenerator.generate();
        DiffgraphFormatter formatter = new DiffgraphFormatter(diffFrame);
        return formatter.format();
    }

    private static Frame _generate(Path recording, Type eventType, AbsoluteTimeRange timeRange, Config config) {
        List<StackBasedRecord> records = new RecordingFileIterator<>(
                recording, new ExecutionSampleEventProcessor(eventType, timeRange))
                .collect();

        FrameTreeBuilder<StackBasedRecord> frameTreeBuilder = new SimpleFrameTreeBuilder(config.threadMode());
        records.forEach(frameTreeBuilder::addRecord);
        return frameTreeBuilder.build();
    }
}
