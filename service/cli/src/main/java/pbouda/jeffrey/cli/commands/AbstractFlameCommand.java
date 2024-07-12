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

package pbouda.jeffrey.cli.commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.cli.CliUtils;
import pbouda.jeffrey.cli.FlamegraphContentReplacer;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.flamegraph.GraphGenerator;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGeneratorImpl;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class AbstractFlameCommand implements Runnable {

    private final GraphType graphType;
    private final GraphGenerator generator;

    public AbstractFlameCommand(GraphType graphType, GraphGenerator generator) {
        this.graphType = graphType;
        this.generator = generator;
    }

    @Option(
            names = {"-e", "--event-type"},
            defaultValue = "jdk.ExecutionSample",
            description = "selects events for generating a flamegraph (e.g. jdk.ExecutionSample)")
    String eventType = Type.EXECUTION_SAMPLE.code();

    @Option(
            names = {"-o", "--output"},
            description = "a path to the file with the generated flamegraph (default is the current folder with a filename '<jfr-name>.html')")
    File outputFile;

    @Option(
            names = {"-w", "--weight"},
            description = "uses event's weight instead of # of samples (currently supported: jdk.ObjectAllocationSample, jdk.ObjectAllocationInNewTLAB, jdk.ObjectAllocationOutsideTLAB, jdk.ThreadPark, jdk.JavaMonitorWait, jdk.JavaMonitorEnter)")
    boolean weight = false;

    abstract Config defineConfig();

    protected String customReplace(String content) {
        return content;
    }

    @Override
    public void run() {
        // Check whether WEIGHT-MODE is supported for the selected EVENT-TYPE
        if (weight && !Type.WEIGHT_SUPPORTED_TYPES.contains(Type.fromCode(eventType))) {
            System.out.println("Unsupported event type for weight-mode visualization. Supported types:");
            for (Type type : Type.WEIGHT_SUPPORTED_TYPES) {
                System.out.println(type.code());
            }
            return;
        }

        Config config = defineConfig();
        ObjectNode flamegraphData = generator.generate(config);

        TimeseriesGenerator timeseriesGenerator = new TimeseriesGeneratorImpl();
        ArrayNode timeseriesData = timeseriesGenerator.generate(config);

        Path outputPath = CliUtils.outputPath(outputFile.toPath(), config.primaryRecording());
        try {
            String content = FlamegraphContentReplacer.flamegraphWithTimeseries(
                    graphType, flamegraphData, timeseriesData, eventType);

            if (weight) {
                content = FlamegraphContentReplacer.replaceUseWeight(content, weight);
            }

            // Specific replacement hook for the specific type of flamegraphs
            content = customReplace(content);

            Files.writeString(outputPath, content);
        } catch (Exception e) {
            System.out.println("Cannot generate a flamegraph: " + e.getMessage());
        }

        System.out.println("Generated: " + outputPath);
    }
}
