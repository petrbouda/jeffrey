package pbouda.jeffrey.flamegraph;

import com.fasterxml.jackson.databind.node.ObjectNode;
import one.Arguments;
import one.ArgumentsBuilder;
import one.FlameGraph;
import one.jfr.JfrReader;
import one.jfr2flame;
import pbouda.jeffrey.common.EventType;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

public class FlamegraphGeneratorImpl implements FlamegraphGenerator {

    @Override
    public ObjectNode generate(Path profilePath, EventType eventType, long startMillis, long endMillis) {
        Arguments args = ArgumentsBuilder.create()
                .withInput(profilePath)
                .withTitle("&nbsp;")
                .withEventType(eventType)
                .withFrom(startMillis)
                .withTo(endMillis)
                .build();

        return _generate(profilePath, args);
    }

    @Override
    public ObjectNode generate(Path profilePath, EventType eventType) {
        Arguments args = ArgumentsBuilder.create()
                .withInput(profilePath)
                .withTitle("&nbsp;")
                .withEventType(eventType)
                .build();

        return _generate(profilePath, args);
    }

    @Override
    public void export(Path targetPath, ObjectNode data) {
        try (OutputStream os = Files.newOutputStream(targetPath);
             PrintStream out = new PrintStream(os, false, Charset.defaultCharset())) {
            new FlameGraph().dumpFromJson(data, out);
        } catch (IOException e) {
            throw new RuntimeException("Cannot export flamegraph to a file: " + targetPath, e);
        }
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
