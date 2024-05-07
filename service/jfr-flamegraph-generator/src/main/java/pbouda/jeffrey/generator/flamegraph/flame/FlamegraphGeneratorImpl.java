package pbouda.jeffrey.generator.flamegraph.flame;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.BytesFormatter;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.generator.flamegraph.FlameGraphBuilder;
import pbouda.jeffrey.generator.flamegraph.processor.ExecutionSampleEventProcessor;
import pbouda.jeffrey.generator.flamegraph.processor.TlabAllocationEventProcessor;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;
import pbouda.jeffrey.generator.flamegraph.tree.FrameTreeBuilder;
import pbouda.jeffrey.generator.flamegraph.tree.SimpleFrameTreeBuilder;
import pbouda.jeffrey.generator.flamegraph.tree.TlabAllocationTreeBuilder;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

import java.util.List;
import java.util.function.Function;

public class FlamegraphGeneratorImpl implements FlamegraphGenerator {

    @Override
    public ObjectNode generate(Config config) {
        if (EventType.ALLOCATIONS.equals(config.eventType())) {
            // allocationClass | weight
            var records = new RecordingFileIterator<>(
                    config.primaryRecording(), new TlabAllocationEventProcessor(
                            EventType.OBJECT_ALLOCATION_SAMPLE, config.primaryTimeRange(), "weight"))
                    .collect();

            return generateFrameTree(records, new TlabAllocationTreeBuilder(
                    config.threadMode()), weight -> BytesFormatter.format(weight) + " Allocated");
        } else {
            var records = new RecordingFileIterator<>(
                    config.primaryRecording(), new ExecutionSampleEventProcessor(
                            config.eventType(), config.primaryTimeRange()))
                    .collect();

            return generateFrameTree(records, new SimpleFrameTreeBuilder(config.threadMode()), null);
        }
    }

    private static <T extends StackBasedRecord> ObjectNode generateFrameTree(
            List<T> records, FrameTreeBuilder<T> builder, Function<Long, String> weightFormatter) {

        records.forEach(builder::addRecord);
        return new FlameGraphBuilder(weightFormatter)
                .dumpToJson(builder.build());
    }
}
