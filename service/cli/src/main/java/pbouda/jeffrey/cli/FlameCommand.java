/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.cli;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.flamegraph.flame.FlamegraphGenerator;
import pbouda.jeffrey.generator.flamegraph.flame.FlamegraphGeneratorImpl;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGeneratorImpl;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Command(
        name = "flame",
        description = "Generates a flamegraph according to the selected event-type",
        mixinStandardHelpOptions = true)
public class FlameCommand implements Runnable {

    @Parameters(paramLabel = "<jfr_file>", description = "one JFR file for fetching events", arity = "1")
    File file;

    @Option(
            names = {"-e", "--event-type"},
            defaultValue = "jdk.ExecutionSample",
            description = "selects events for generating a flamegraph (e.g. jdk.ExecutionSample)")
    String eventType;

    @Option(
            names = {"-o", "--output"},
            description = "a path to the file with the generated flamegraph (default is the current folder with a filename '<jfr-name>.html')")
    File outputFile;

    @Override
    public void run() {
        Config config = Config.primaryBuilder()
                .withPrimaryRecording(file.toPath())
                .withEventType(Type.fromCode(eventType))
                .build();

        FlamegraphGenerator generator = new FlamegraphGeneratorImpl();
        ObjectNode flamegraphData = generator.generate(config);

        TimeseriesGenerator timeseriesGenerator = new TimeseriesGeneratorImpl();
        ArrayNode timeseriesData = timeseriesGenerator.generate(config);

        try {
            String content = FlamegraphContentReplacer.primaryFlamegraphWithTimeseries(flamegraphData, timeseriesData);
            Files.writeString(outputPath(), content);
        } catch (RuntimeException | IOException e) {
            System.out.println("Cannot generate a flamegraph: " + e.getMessage());
        }
    }

    private Path outputPath() {
        if (outputFile == null) {
            String currentDir = System.getProperty("user.dir");
            String jfrFilename = file.toPath().getFileName().toString();
            String outputFilename = jfrFilename.replace(".jfr", ".html");
            return Path.of(currentDir, outputFilename);
        } else {
            return outputFile.toPath();
        }
    }
}
