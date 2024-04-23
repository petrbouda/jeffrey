package pbouda.jeffrey.graph.flame;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.graph.FlameGraphBuilder;
import pbouda.jeffrey.graph.Frame;
import pbouda.jeffrey.graph.StackTraceBuilder;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;
import pbouda.jeffrey.jfrparser.jdk.StackBasedRecord;
import pbouda.jeffrey.jfrparser.jdk.StacktraceBasedEventProcessor;

import java.util.List;

public class FlamegraphGeneratorImpl implements FlamegraphGenerator {

    @Override
    public ObjectNode generate(Config config) {
        List<StackBasedRecord> records = new RecordingFileIterator<>(
                config.primaryRecording(), new StacktraceBasedEventProcessor(config.eventType(), config.primaryTimeRange()))
                .collect();

        StackTraceBuilder stackTraceBuilder = new StackTraceBuilder();
        records.forEach(r -> stackTraceBuilder.addStackTrace(r.stackTrace()));
        Frame tree = stackTraceBuilder.build();

        FlameGraphBuilder builder = new FlameGraphBuilder();
        return builder.dumpToJson(tree);
    }
}
