package pbouda.jeffrey.flamegraph.diff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import one.*;
import one.jfr.JfrReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.TreeMap;

public class DiffFlamegraphGeneratorImpl implements DiffFlamegraphGenerator {

    @Override
    public ObjectNode generate(Request request) {
        Arguments baselineArgs = arguments(request.baselinePath(), request);
        Arguments comparisonArgs = arguments(request.comparisonPath(), request);

        Frame baseline = _generate(request.baselinePath(), baselineArgs);
        Frame comparison = _generate(request.comparisonPath(), comparisonArgs);

        DiffTreeGenerator treeGenerator = new DiffTreeGenerator(baseline, comparison);
        DiffTree generate = treeGenerator.generate();
        DiffFlamegraphFormatter formatter = new DiffFlamegraphFormatter(generate);
        return formatter.format();
    }

    private static Arguments arguments(Path profilePath, Request request) {
        return ArgumentsBuilder.create()
                .withInput(profilePath)
                .withTitle("&nbsp;")
                .withEventType(request.eventType())
                .withFrom(request.startMillis())
                .withTo(request.endMillis())
                .build();
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
}
