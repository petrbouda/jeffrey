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

import pbouda.jeffrey.cli.replacer.ContentReplacer;
import pbouda.jeffrey.common.*;
import pbouda.jeffrey.generator.basic.StartEndTimeCollector;
import pbouda.jeffrey.generator.basic.StartEndTimeEventProcessor;
import pbouda.jeffrey.generator.flamegraph.GraphGenerator;
import pbouda.jeffrey.generator.flamegraph.flame.FlamegraphGeneratorImpl;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

@Command(
        name = FlameCommand.COMMAND_NAME,
        description = "Generates a Flamegraph (default: jdk.ExecutionSample)",
        mixinStandardHelpOptions = true)
public class FlameCommand extends AbstractFlameCommand {

    public static final String COMMAND_NAME = "flame";

    public FlameCommand() {
        super(GraphType.PRIMARY, flamegraphGeneratorSupplier());
    }

    private static Function<Config, GraphGenerator> flamegraphGeneratorSupplier() {
        return config -> new FlamegraphGeneratorImpl();
    }

    @Parameters(paramLabel = "<jfr_file>", description = "one JFR file for fetching events", arity = "1")
    File file;

    @Option(
            names = {"-t", "--thread"},
            description = "Groups stacktraces omitted on the particular thread")
    boolean threadMode = false;

    @Option(
            names = {"-s", "--search-pattern"},
            description = "Only for timeseries (timeseries cannot dynamically searches in the generated file, only the flamegraph can)")
    String searchPattern;

    @Override
    protected String customReplace(String content) {
        return searchPattern != null ? ContentReplacer.replaceSearch(content, searchPattern) : content;
    }

    @Override
    ConfigBuilder<?> defineConfig() {
        Path primaryPath = CommandUtils.replaceTilda(file.toPath());
        CommandUtils.checkPathExists(primaryPath);

        ProfilingStartEnd primaryStartEndTime = JdkRecordingIterators.automaticAndCollectPartial(
                List.of(primaryPath),
                StartEndTimeEventProcessor::new,
                new StartEndTimeCollector());

        if (primaryStartEndTime.isInvalid()) {
            System.out.println("The recording does not contain a mandatory event: jdk.ActiveRecording");
            System.exit(1);
        }

        ConfigBuilder<?> builder = Config.primaryBuilder()
                .withPrimaryStartEnd(primaryStartEndTime)
                .withEventType(Type.fromCode(eventType))
                .withThreadMode(threadMode)
                .withSearchPattern(validateSearchPattern(searchPattern))
                .withCollectWeight(weight);

        if (Files.isDirectory(primaryPath)) {
            builder.withPrimaryRecordingDir(primaryPath);
        } else {
            builder.withPrimaryRecording(primaryPath);
        }

        return builder;
    }

    private static String validateSearchPattern(String searchPattern) {
        if (searchPattern != null && !searchPattern.trim().isEmpty()) {
            return searchPattern.trim();
        }
        return null;
    }
}
