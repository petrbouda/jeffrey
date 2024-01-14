package pbouda.jeffrey.flamegraph;

import one.Arguments;
import one.ArgumentsBuilder;
import one.FlameGraph;
import one.jfr.JfrReader;
import one.jfr2flame;
import pbouda.jeffrey.WorkingDirectory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

import static java.nio.file.StandardOpenOption.*;

public class FlamegraphGeneratorImpl implements FlamegraphGenerator {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    @Override
    public void generate(String jfrName, String outputName, EventType eventType, long startMillis, long endMillis) {
        Path profilePath = WorkingDirectory.PROFILES_DIR.resolve(jfrName);

        Arguments args = ArgumentsBuilder.create()
                .withInput(profilePath)
                .withTitle("")
                .withEventType(eventType)
                .withFrom(startMillis)
                .withTo(endMillis)
                .build();

        _generate(profilePath, outputName, args);
    }

    @Override
    public void generate(String jfrName, String outputName, EventType eventType) {
        Path profilePath = WorkingDirectory.PROFILES_DIR.resolve(jfrName);

        Arguments args = ArgumentsBuilder.create()
                .withInput(profilePath)
                .withTitle("")
                .withEventType(eventType)
                .build();

        _generate(profilePath, outputName, args);
    }

    private static void _generate(Path profilePath, String outputName, Arguments args){
        Path rawDataPath = WorkingDirectory.GENERATED_DIR.resolve(outputName + ".data");

        try {
            String htmlContent = generateContent(profilePath, args);
            String rawData = generateRawData(htmlContent);
            Files.writeString(rawDataPath, rawData, CREATE, TRUNCATE_EXISTING, WRITE);
        } catch (IOException e) {
            throw new RuntimeException(STR."Cannot generate a flamegraph data: input=\{profilePath}", e);
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
