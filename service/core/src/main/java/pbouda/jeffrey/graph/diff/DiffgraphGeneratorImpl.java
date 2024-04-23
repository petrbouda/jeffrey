package pbouda.jeffrey.graph.diff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.graph.Frame;
import pbouda.jeffrey.graph.StackTraceBuilder;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;
import pbouda.jeffrey.jfrparser.jdk.StackBasedRecord;
import pbouda.jeffrey.jfrparser.jdk.StacktraceBasedEventProcessor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class DiffgraphGeneratorImpl implements DiffgraphGenerator {

    @Override
    public ObjectNode generate(Config config) {
        // We need to correlate start-time of the primary and secondary profiles
        // Secondary profile will be moved in time to start at the same time as primary profile
        Frame comparison, baseline;
        if (config.primaryTimeRange() == AbsoluteTimeRange.UNLIMITED || config.secondaryTimeRange() == AbsoluteTimeRange.UNLIMITED) {
            comparison = _generate(config.primaryRecording(), config.eventType(), Duration.ZERO, config.primaryTimeRange());
            baseline = _generate(config.secondaryRecording(), config.eventType(), Duration.ZERO, config.secondaryTimeRange());
        } else {
            Duration timeShift = Duration.between(config.secondaryStart(), config.primaryStart());
            comparison = _generate(config.primaryRecording(), config.eventType(), Duration.ZERO, config.primaryTimeRange());
            baseline = _generate(config.secondaryRecording(), config.eventType(), timeShift, config.secondaryTimeRange().shift(timeShift));
        }

        DiffTreeGenerator treeGenerator = new DiffTreeGenerator(baseline, comparison);
        DiffFrame diffFrame = treeGenerator.generate();
        DiffgraphFormatter formatter = new DiffgraphFormatter(diffFrame);
        return formatter.format();
    }

    private static Frame _generate(Path recording, EventType eventType, Duration timeShift, AbsoluteTimeRange timeRange) {
        List<StackBasedRecord> records = new RecordingFileIterator<>(
                recording, new StacktraceBasedEventProcessor(eventType, timeShift, timeRange))
                .collect();

        StackTraceBuilder stackTraceBuilder = new StackTraceBuilder();
        records.forEach(r -> stackTraceBuilder.addStackTrace(r.stackTrace()));
        return stackTraceBuilder.build();
    }
}
