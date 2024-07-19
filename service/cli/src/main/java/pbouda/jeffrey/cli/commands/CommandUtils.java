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

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordingFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public abstract class CommandUtils {

    private static final String STACKTRACE_TYPE_NAME = "jdk.settings.StackTrace";

    /**
     * Generates a standardized path for the output. If the `outputPath` is null,
     * then the path is generated from the primary profile's path in the current folder.
     *
     * @param outputFile provided `outputFile` very likely from CLI parameter.
     * @param primary    primary profile's path.
     * @return provided `outputPath` or the generated one.
     */
    public static Path outputPath(File outputFile, Path primary) {
        if (outputFile == null) {
            String currentDir = System.getProperty("user.dir");
            String jfrFilename = primary.getFileName().toString();
            String outputFilename = jfrFilename.replace(".jfr", ".html");
            return Path.of(currentDir, outputFilename);
        } else {
            return outputFile.toPath();
        }
    }

    /**
     * Goes ever all event types inside the recording and select only those which contain
     * stacktrace to be able to build flamegraph.
     *
     * @param recording provided recording file.
     * @return list of all event types with a stacktrace.
     * @throws IOException cannot list event types from the recording.
     */
    public static List<EventType> listStackBasedEventTypes(Path recording) throws IOException {
        try (RecordingFile rec = new RecordingFile(recording)) {
            return rec.readEventTypes().stream()
                    .filter(CommandUtils::containStacktrace)
                    .toList();
        }
    }

    private static boolean containStacktrace(EventType eventType) {
        return eventType.getSettingDescriptors().stream()
                .anyMatch(desc -> desc.getTypeName().equals(STACKTRACE_TYPE_NAME));
    }

    public static Path writeToOutput(Path outputPath, String content) {
        try {
            return Files.writeString(outputPath, content);
        } catch (Exception e) {
            System.out.println("Cannot generate a graph: " + e.getMessage());
            System.exit(1);
            return null;
        }
    }
}
