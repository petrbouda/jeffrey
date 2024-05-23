package pbouda.jeffrey.generator.flamegraph.flame;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.BytesFormatter;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.DurationFormatter;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.flamegraph.FlameGraphBuilder;
import pbouda.jeffrey.generator.flamegraph.processor.ExecutionSampleEventProcessor;
import pbouda.jeffrey.generator.flamegraph.processor.BlockingEventProcessor;
import pbouda.jeffrey.generator.flamegraph.processor.AllocationEventProcessor;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;
import pbouda.jeffrey.generator.flamegraph.tree.FrameTreeBuilder;
import pbouda.jeffrey.generator.flamegraph.tree.BlockingTreeBuilder;
import pbouda.jeffrey.generator.flamegraph.tree.SimpleFrameTreeBuilder;
import pbouda.jeffrey.generator.flamegraph.tree.AllocationTreeBuilder;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;

import java.util.List;
import java.util.function.Function;

public class FlamegraphGeneratorImpl implements FlamegraphGenerator {

    @Override
    public ObjectNode generate(Config config) {
        if (Type.OBJECT_ALLOCATION_IN_NEW_TLAB.equals(config.eventType())) {
            List<Type> types = List.of(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, Type.OBJECT_ALLOCATION_OUTSIDE_TLAB);
            var processor = new AllocationEventProcessor(types, config.primaryTimeRange());
            return generateAllocationTree(config, processor);

        } else if (Type.OBJECT_ALLOCATION_SAMPLE.equals(config.eventType())) {
            var processor = new AllocationEventProcessor(Type.OBJECT_ALLOCATION_SAMPLE, config.primaryTimeRange());
            return generateAllocationTree(config, processor);

        } else if (Type.JAVA_MONITOR_ENTER.equals(config.eventType())) {
            return generateMonitorTree(config, Type.JAVA_MONITOR_ENTER);
        } else if (Type.JAVA_MONITOR_WAIT.equals(config.eventType())) {
            return generateMonitorTree(config, Type.JAVA_MONITOR_WAIT);
        } else if (Type.THREAD_PARK.equals(config.eventType())) {
            return generateMonitorTree(config, Type.THREAD_PARK);
        } else {
            var records = new RecordingFileIterator<>(
                    config.primaryRecording(), new ExecutionSampleEventProcessor(
                    config.eventType(), config.primaryTimeRange()))
                    .collect();

            return generateFrameTree(records, new SimpleFrameTreeBuilder(config.threadMode()), null);
        }
    }

    private static ObjectNode generateAllocationTree(Config config, AllocationEventProcessor processor) {
        var records = new RecordingFileIterator<>(config.primaryRecording(), processor)
                .collect();

        return generateFrameTree(records, new AllocationTreeBuilder(
                config.threadMode()), weight -> BytesFormatter.format(weight) + " Allocated");
    }

    private static ObjectNode generateMonitorTree(Config config, Type eventType) {
        var records = new RecordingFileIterator<>(
                config.primaryRecording(),
                new BlockingEventProcessor(config.primaryTimeRange(), eventType))
                .collect();

        return generateFrameTree(records, new BlockingTreeBuilder(
                config.threadMode()), weight -> DurationFormatter.format(weight) + " Blocked");
    }

    private static <T extends StackBasedRecord> ObjectNode generateFrameTree(
            List<T> records, FrameTreeBuilder<T> builder, Function<Long, String> weightFormatter) {

        records.forEach(builder::addRecord);
        return new FlameGraphBuilder(weightFormatter)
                .dumpToJson(builder.build());
    }
}
