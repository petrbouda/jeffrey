package pbouda.jeffrey.flamegraph;

import one.Arguments;
import one.ArgumentsBuilder;
import one.FlameGraph;
import one.jfr.JfrReader;
import one.jfr2flame;
import pbouda.jeffrey.WorkingDirectory;

import java.io.IOException;
import java.nio.file.Path;

public class FlamegraphGeneratorImpl implements FlamegraphGenerator {

    @Override
    public Path generate(String jfrName, String graphName, EventType eventType) {
        Path profilePath = WorkingDirectory.PROFILES_DIR.resolve(jfrName);
        Path graphPath = WorkingDirectory.GENERATED_DIR.resolve(graphName);

        Arguments args = ArgumentsBuilder.create()
                .withInput(profilePath)
                .withOutput(graphPath)
                .withTitle("")
                .withEventType(eventType)
                .build();

        try (JfrReader jfr = new JfrReader(profilePath.toString())) {
            FlameGraph fg = new FlameGraph(args);
            new jfr2flame(jfr, args).convert(fg);
            fg.dump();
        } catch (IOException e) {
            throw new RuntimeException(STR."Cannot generate a flamegraph: input=\{profilePath}", e);
        }

        return graphPath;
    }
}
