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

import pbouda.jeffrey.common.*;
import pbouda.jeffrey.generator.basic.ProfilingStartTimeProcessor;
import pbouda.jeffrey.generator.flamegraph.diff.DiffgraphGeneratorImpl;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Command(
        name = FlameDiffCommand.COMMAND_NAME,
        description = "Generates a Differential Flamegraph (default: jdk.ExecutionSample)",
        mixinStandardHelpOptions = true)
public class FlameDiffCommand extends AbstractFlameCommand {

    public static final String COMMAND_NAME = "flame-diff";

    @Parameters(paramLabel = "<jfr_file>", description = "Primary and secondary JFR files", arity = "2")
    File[] file;

    public FlameDiffCommand() {
        super(GraphType.DIFFERENTIAL, new DiffgraphGeneratorImpl());
    }

    @Override
    ConfigBuilder<?> defineConfig() {
        Path primaryPath = CommandUtils.replaceTilda(file[0].toPath());
        CommandUtils.checkPathExists(primaryPath);

        Path secondaryPath = CommandUtils.replaceTilda(file[1].toPath());
        CommandUtils.checkPathExists(secondaryPath);

        CommandUtils.bothFileOrDirectory(primaryPath, secondaryPath);

        var primaryStartTime = JdkRecordingIterators.fileOrDirAndCollectIdentical(
                primaryPath, new ProfilingStartTimeProcessor());
        var secondaryStartTime = JdkRecordingIterators.fileOrDirAndCollectIdentical(
                secondaryPath, new ProfilingStartTimeProcessor());

        DiffConfigBuilder configBuilder = Config.differentialBuilder()
                .withPrimaryRecording(primaryPath)
                .withPrimaryStart(primaryStartTime)
                .withSecondaryRecording(secondaryPath)
                .withSecondaryStart(secondaryStartTime)
                .withEventType(Type.fromCode(eventType))
                .withCollectWeight(weight);

        if (Files.isRegularFile(primaryPath)) {
            configBuilder
                    .withPrimaryRecording(primaryPath)
                    .withSecondaryRecording(secondaryPath);
        } else {
            configBuilder
                    .withPrimaryRecordingDir(primaryPath)
                    .withSecondaryRecordingDir(secondaryPath);
        }
        return configBuilder;
    }
}
