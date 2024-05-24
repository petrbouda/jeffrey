package pbouda.jeffrey.generator.flamegraph.diff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.flamegraph.Frame;
import pbouda.jeffrey.generator.flamegraph.processor.AllocationEventProcessor;
import pbouda.jeffrey.generator.flamegraph.processor.BasicSampleEventProcessor;
import pbouda.jeffrey.generator.flamegraph.processor.StacktraceBasedEventProcessor;
import pbouda.jeffrey.generator.flamegraph.record.AllocationRecord;
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
        Frame primary;
        Frame secondary;

        if (Type.OBJECT_ALLOCATION_IN_NEW_TLAB.equals(config.eventType())) {
            List<Type> types = List.of(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, Type.OBJECT_ALLOCATION_OUTSIDE_TLAB);
            primary = _generate(config.primaryRecording(), new AllocationEventProcessor(types, config.primaryTimeRange()));
            secondary = _generate(config.secondaryRecording(), new AllocationEventProcessor(types, config.secondaryTimeRange()));

        } else if (Type.OBJECT_ALLOCATION_SAMPLE.equals(config.eventType())) {
            primary = _generate(config.primaryRecording(), new AllocationEventProcessor(Type.OBJECT_ALLOCATION_SAMPLE, config.primaryTimeRange()));
            secondary = _generate(config.secondaryRecording(), new AllocationEventProcessor(Type.OBJECT_ALLOCATION_SAMPLE, config.secondaryTimeRange()));

        } else {
            primary = _generate(config.primaryRecording(), new BasicSampleEventProcessor(config.eventType(), config.primaryTimeRange()));
            secondary = _generate(config.secondaryRecording(), new BasicSampleEventProcessor(config.eventType(), config.secondaryTimeRange()));
        }

        DiffTreeGenerator treeGenerator = new DiffTreeGenerator(primary, secondary);
        DiffFrame diffFrame = treeGenerator.generate();
        DiffgraphFormatter formatter = new DiffgraphFormatter(diffFrame);
        return formatter.format();
    }

    private static Frame _generate(
            Path recording, StacktraceBasedEventProcessor<? extends StackBasedRecord> processor) {

        List<? extends StackBasedRecord> records = new RecordingFileIterator<>(recording, processor)
                .collect();

        FrameTreeBuilder<StackBasedRecord> frameTreeBuilder = new SimpleFrameTreeBuilder(false);
        records.forEach(frameTreeBuilder::addRecord);
        return frameTreeBuilder.build();
    }
}
