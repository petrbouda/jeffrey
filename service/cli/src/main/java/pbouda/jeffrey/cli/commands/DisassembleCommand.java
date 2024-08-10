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

import pbouda.jeffrey.tools.api.JfrTool;
import pbouda.jeffrey.tools.impl.jdk.JdkJfrTool;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Command(
        name = DisassembleCommand.COMMAND_NAME,
        description = "Splits a single JFR file into a multiple JFR files based on the number of internal chunks",
        mixinStandardHelpOptions = true)
public class DisassembleCommand implements Runnable {

    public static final String COMMAND_NAME = "disassemble";

    @Parameters(index = "0", paramLabel = "<jfr_file>", description = "JFR file to be divided into multiple files", arity = "1")
    File file;

    @Parameters(index = "1", paramLabel = "<output_dir>", description = "output directory for newly created JFR files", arity = "1")
    File outputDir;

    @Override
    public void run() {
        try {
            Path jfrPath = CommandUtils.replaceTilda(file.toPath());
            Path outputPath = CommandUtils.replaceTilda(outputDir.toPath());

            System.out.println("Disassembling JFR file: " + jfrPath);
            System.out.println("Output directory: " + outputPath);

            if (!jfrPath.toFile().exists()) {
                System.err.println("The specified JFR file does not exist: " + jfrPath);
                System.exit(1);
            }

            if (!outputPath.toFile().exists()) {
                Files.createDirectories(outputPath);
            } else {
                if (!isDirectoryEmpty(outputPath)) {
                    System.err.println("Output directory is not empty (it could cause problems later): " + outputPath);
                    System.exit(1);
                }
            }

            JfrTool jfrTool = new JdkJfrTool();
            jfrTool.initialize();
            jfrTool.disassemble(jfrPath, outputPath);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static boolean isDirectoryEmpty(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        }
    }
}
