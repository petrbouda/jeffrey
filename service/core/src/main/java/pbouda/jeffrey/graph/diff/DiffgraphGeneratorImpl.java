package pbouda.jeffrey.graph.diff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import one.*;
import one.jfr.JfrReader;
import pbouda.jeffrey.TimeRange;

import java.io.IOException;
import java.nio.file.Path;

public class DiffgraphGeneratorImpl implements DiffgraphGenerator {

    @Override
    public ObjectNode generate(Request request) {
        // We need to correlate start-time of the primary and secondary profiles
        // Secondary profile will be moved in time to start at the same time as primary profile
        long timeShift = calculateTimeShift(request);
        Request modifiedRequest = request.toAbsoluteTime();

        Arguments primaryArgs = arguments(modifiedRequest.primaryPath(), modifiedRequest);
        Arguments secondaryArgs = arguments(modifiedRequest.secondaryPath(), modifiedRequest.shiftTimeRange(timeShift));

        Frame comparison = _generate(modifiedRequest.primaryPath(), primaryArgs);
        Frame baseline = _generate(modifiedRequest.secondaryPath(), secondaryArgs);

        DiffTreeGenerator treeGenerator = new DiffTreeGenerator(baseline, comparison);
        DiffFrame diffFrame = treeGenerator.generate();
        DiffgraphFormatter formatter = new DiffgraphFormatter(diffFrame);
        return formatter.format();
    }

    private static Arguments arguments(Path profilePath, Request request) {
        ArgumentsBuilder args = ArgumentsBuilder.create()
                .withInput(profilePath)
                .withTitle("&nbsp;")
                .withEventType(request.eventType());

        TimeRange timeRange = request.timeRange();
        if (timeRange != null) {
            args.withFrom(timeRange.start());
            args.withTo(timeRange.end());
        }

        return args.build();
    }

    private static Frame _generate(Path profilePath, Arguments args) {
        try (JfrReader jfr = new JfrReader(profilePath.toString())) {
            FlameGraph fg = new FlameGraph(args);
            new jfr2flame(jfr, args).convert(fg);
            return fg.getRoot();
        } catch (IOException e) {
            throw new RuntimeException("Cannot generate a flamegraph data: " + profilePath, e);
        }
    }

    private static long calculateTimeShift(Request request) {
        long primary = request.primaryStart().toEpochMilli();
        long secondary = request.secondaryStart().toEpochMilli();
        return secondary - primary;
    }
}
