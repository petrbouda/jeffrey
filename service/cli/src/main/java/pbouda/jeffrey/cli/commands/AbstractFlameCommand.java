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

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.cli.CliParameterCheck;
import pbouda.jeffrey.cli.replacer.ContentReplacer;
import pbouda.jeffrey.cli.replacer.FlamegraphContentReplacer;
import pbouda.jeffrey.common.*;
import pbouda.jeffrey.generator.flamegraph.GraphGenerator;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGeneratorImpl;
import picocli.CommandLine.Option;

import java.io.File;
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
            description = "Selects events for generating a flamegraph (e.g. jdk.ExecutionSample)")
    String eventType = Type.EXECUTION_SAMPLE.code();

    @Option(
            names = {"-o", "--output"},
            description = "Path to the file with the generated flamegraph (default is the current folder with a filename '<jfr-name>.html')")
    File outputFile;

    @Option(
            names = {"-w", "--weight"},
            description = "Uses event's weight instead of # of samples (currently supported: jdk.ObjectAllocationSample, jdk.ObjectAllocationInNewTLAB, jdk.ObjectAllocationOutsideTLAB, jdk.ThreadPark, jdk.JavaMonitorWait, jdk.JavaMonitorEnter)")
    boolean weight = false;

    @Option(
            names = {"--with-timeseries"},
            description = "Includes Timeseries graph with a Flamegraph (it's `true` by default, set `false` to have only the Flamegraph)")
    boolean withTimeseries = true;

    @Option(
            names = {"--start-time"},
            description = "Relative start in milliseconds from the beginning of the JFR file")
    long startTime = Long.MIN_VALUE;

    @Option(
            names = {"--end-time"},
            description = "Relative end in milliseconds from the beginning of the JFR file")
    long endTime = Long.MIN_VALUE;

    abstract ConfigBuilder<?> defineConfig();

    protected String customReplace(String content) {
        return content;
    }

    @Override
    public void run() {
        CliParameterCheck.weight(weight, eventType);
        TimeRange timeRange = createTimeRange();

        Config config = defineConfig()
                .withTimeRange(timeRange)
                .build();

        JsonNode flamegraphData = generator.generate(config);

        String content;
        if (withTimeseries) {
            TimeseriesGenerator timeseriesGenerator = new TimeseriesGeneratorImpl();
            JsonNode timeseriesData = timeseriesGenerator.generate(config);
            content = FlamegraphContentReplacer.withTimeseries(
                    graphType, flamegraphData, timeseriesData, eventType);
        } else {
            content = FlamegraphContentReplacer.flamegraphOnly(
                    graphType, flamegraphData, eventType);
        }

        if (weight) {
            content = ContentReplacer.enableUseWeight(content);
        }

        // Specific replacement hook for the specific type of flamegraphs
        content = customReplace(content);

        Path outputPath = CommandUtils.outputPath(outputFile, config.primaryRecording());

        CommandUtils.writeToOutput(outputPath, content);
        System.out.println("Generated: " + outputPath);
    }

    public TimeRange createTimeRange() {
        if (startTime != Long.MIN_VALUE && endTime != Long.MIN_VALUE) {
            return new RelativeTimeRange(startTime, endTime);
        } else if (startTime != Long.MIN_VALUE && endTime == Long.MIN_VALUE) {
            return RelativeTimeRange.justStart(startTime);
        } else if (startTime == Long.MIN_VALUE && endTime != Long.MIN_VALUE) {
            return RelativeTimeRange.justEnd(endTime);
        }

        return AbsoluteTimeRange.UNLIMITED;
    }
}
