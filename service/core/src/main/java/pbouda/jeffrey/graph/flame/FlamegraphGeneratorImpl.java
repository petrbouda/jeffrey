package pbouda.jeffrey.graph.flame;

import com.fasterxml.jackson.databind.node.ObjectNode;
import one.Arguments;
import one.ArgumentsBuilder;
import one.FlameGraph;
import one.jfr.JfrReader;
import one.jfr2flame;
import pbouda.jeffrey.Json;
import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.graph.FlameGraphBuilder;
import pbouda.jeffrey.graph.Frame;
import pbouda.jeffrey.graph.StackTraceBuilder;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;
import pbouda.jeffrey.jfrparser.jdk.StackBasedRecord;
import pbouda.jeffrey.jfrparser.jdk.StacktraceBasedEventProcessor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class FlamegraphGeneratorImpl implements FlamegraphGenerator {

    @Override
    public ObjectNode generate(Path profilePath, EventType eventType, TimeRange timeRange) {
        Arguments args = ArgumentsBuilder.create()
                .withInput(profilePath)
                .withTitle("&nbsp;")
                .withEventType(eventType)
                .withFrom(timeRange.start())
                .withTo(timeRange.end())
                .build();

        return _generate(profilePath, args);
    }

    @Override
    public ObjectNode generate(Path profilePath, EventType eventType) {
        List<StackBasedRecord> records = new RecordingFileIterator<>(
                profilePath, new StacktraceBasedEventProcessor(EventType.EXECUTION_SAMPLE))
                .collect();

        StackTraceBuilder stackTraceBuilder = new StackTraceBuilder();
        records.forEach(r -> stackTraceBuilder.addStackTrace(r.stackTrace()));

        Frame tree = stackTraceBuilder.build();
        FlameGraphBuilder builder = new FlameGraphBuilder();
        return builder.dumpToJson(tree);
    }

    private static ObjectNode _generate(Path profilePath, Arguments args) {
        try (JfrReader jfr = new JfrReader(profilePath.toString())) {
            FlameGraph fg = new FlameGraph(args);
            new jfr2flame(jfr, args).convert(fg);
            return fg.dumpToJson();
        } catch (IOException e) {
            throw new RuntimeException("Cannot generate a flamegraph data: " + profilePath, e);
        }
    }
}
