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
import pbouda.jeffrey.cli.replacer.SubSecondContentReplacer;
import pbouda.jeffrey.common.GraphType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Path;

@Command(
        name = SubSecondCommand.COMMAND_NAME,
        description = "Generates Sub-Second graph (the first 5 minutes)",
        mixinStandardHelpOptions = true)
public class SubSecondCommand extends AbstractSubSecondCommand {

    public static final String COMMAND_NAME = "sub-second";

    @Parameters(paramLabel = "<jfr_file>", description = "One JFR file for generating sub-second graph", arity = "1")
    File file;

    @Override
    public void run() {
        CliParameterCheck.weight(weight, eventType);

        Path primaryRecording = CommandUtils.replaceTilda(file.toPath());

        JsonNode primaryData = generateData(primaryRecording);

        Path outputPath = CommandUtils.outputPath(outputFile, primaryRecording);

        String content = SubSecondContentReplacer.loadContentAndReplace(eventType, GraphType.PRIMARY);
        content = SubSecondContentReplacer.primary(content, primaryData);
        content = SubSecondContentReplacer.primaryCommand(
                content, eventType, CommandUtils.outputDir(outputPath), primaryRecording);

        if (weight) {
            content = ContentReplacer.enableUseWeight(content);
        }
        CommandUtils.writeToOutput(outputPath, content);
        System.out.println("Generated: " + outputPath);
    }
}
