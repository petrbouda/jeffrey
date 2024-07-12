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

import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.basic.ProfilingStartTimeProcessor;
import pbouda.jeffrey.generator.flamegraph.diff.DiffgraphGeneratorImpl;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Path;

@Command(
        name = "flame-diff",
        description = "Generates a Differential Flamegraph (default: jdk.ExecutionSample)",
        mixinStandardHelpOptions = true)
public class FlameDiffCommand extends AbstractFlameCommand {

    @Parameters(paramLabel = "<jfr_file>", description = "primary and secondary JFR files", arity = "2")
    File[] file;

    public FlameDiffCommand() {
        super(GraphType.DIFFERENTIAL, new DiffgraphGeneratorImpl());
    }

    @Override
    Config defineConfig() {
        Path primary = file[0].toPath();
        Path secondary = file[1].toPath();

        var primaryStartTime = new RecordingFileIterator<>(primary, new ProfilingStartTimeProcessor())
                .collect();
        var secondaryStartTime = new RecordingFileIterator<>(secondary, new ProfilingStartTimeProcessor())
                .collect();

        return Config.differentialBuilder()
                .withPrimaryRecording(primary)
                .withPrimaryStart(primaryStartTime)
                .withSecondaryRecording(secondary)
                .withSecondaryStart(secondaryStartTime)
                .withEventType(Type.fromCode(eventType))
                .withCollectWeight(weight)
                .build();
    }
}
