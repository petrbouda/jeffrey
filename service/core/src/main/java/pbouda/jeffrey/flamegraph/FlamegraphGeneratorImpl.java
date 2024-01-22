package pbouda.jeffrey.flamegraph;

import one.Arguments;
import one.ArgumentsBuilder;
import one.FlameGraph;
import one.jfr.JfrReader;
import one.jfr2flame;
import pbouda.jeffrey.manager.EventType;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Scanner;

public class FlamegraphGeneratorImpl implements FlamegraphGenerator {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    @Override
    public byte[] generate(Path profilePath, EventType eventType, long startMillis, long endMillis) {
        Arguments args = ArgumentsBuilder.create()
                .withInput(profilePath)
                .withTitle("")
                .withEventType(eventType)
                .withFrom(startMillis)
                .withTo(endMillis)
                .build();

        return _generate(profilePath, args);
    }

    @Override
    public byte[] generate(Path profilePath, EventType eventType) {
        Arguments args = ArgumentsBuilder.create()
                .withInput(profilePath)
                .withTitle("")
                .withEventType(eventType)
                .build();

        return _generate(profilePath, args);
    }

    private static byte[] _generate(Path profilePath, Arguments args) {
        try {
            String htmlContent = generateContent(profilePath, args);
            return generateRawData(htmlContent).getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Cannot generate a flamegraph data: " + profilePath, e);
        }
    }

    private static String generateContent(Path profilePath, Arguments args) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JfrReader jfr = new JfrReader(profilePath.toString());
             BufferedOutputStream bos = new BufferedOutputStream(baos, 32768);
             PrintStream out = new PrintStream(bos, false, StandardCharsets.UTF_8)) {

            FlameGraph fg = new FlameGraph(args);
            new jfr2flame(jfr, args).convert(fg);
            fg.dump(out);
        }

        return baos.toString();
    }

    private static String generateRawData(String content) throws IOException {
        StringBuilder builder = new StringBuilder();

        int maxLevel = -1;
        Scanner scanner = new Scanner(content);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (!line.startsWith("f(")) {
                continue;
            }

            int level = parseLevel(line);
            if (level > maxLevel) {
                maxLevel = level;
            }

            builder.append(line).append(LINE_SEPARATOR);
        }
        scanner.close();

        return maxLevel + LINE_SEPARATOR + builder;
    }

    private static int parseLevel(String line) {
        int endOfLevelIndex = line.indexOf(",");
        return Integer.parseInt(line.substring(2, endOfLevelIndex));
    }
}
