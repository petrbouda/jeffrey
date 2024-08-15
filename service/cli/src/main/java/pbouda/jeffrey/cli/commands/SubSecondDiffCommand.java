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
        name = SubSecondDiffCommand.COMMAND_NAME,
        description = "Generates Differential Sub-Second graph (the first 5 minutes)",
        mixinStandardHelpOptions = true)
public class SubSecondDiffCommand extends AbstractSubSecondCommand {

    public static final String COMMAND_NAME = "sub-second-diff";

    @Parameters(paramLabel = "<jfr_file>", description = "Primary and secondary JFR files", arity = "2")
    File[] file;

    @Override
    public void run() {
        try {
            _run();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void _run() {
        CliParameterCheck.weight(weight, eventType);

        Path primaryPath = CommandUtils.replaceTilda(file[0].toPath());
        Path secondaryPath = CommandUtils.replaceTilda(file[1].toPath());

        CommandUtils.checkPathExists(primaryPath);
        CommandUtils.checkPathExists(secondaryPath);
        CommandUtils.bothFileOrDirectory(primaryPath, secondaryPath);

        JsonNode primaryData = generateData(primaryPath);
        JsonNode secondaryData = generateData(secondaryPath);

        Path outputPath = CommandUtils.outputPath(outputFile, primaryPath);

        String content = SubSecondContentReplacer.loadContentAndReplace(eventType, GraphType.DIFFERENTIAL);
        content = SubSecondContentReplacer.primary(content, primaryData);
        content = SubSecondContentReplacer.secondary(content, secondaryData);
        content = SubSecondContentReplacer.secondaryCommand(
                content, eventType, CommandUtils.outputDir(outputPath), primaryPath, secondaryPath);

        if (weight) {
            content = ContentReplacer.enableUseWeight(content);
        }

        CommandUtils.writeToOutput(outputPath, content);
        System.out.println("Generated: " + outputPath);
    }
}
