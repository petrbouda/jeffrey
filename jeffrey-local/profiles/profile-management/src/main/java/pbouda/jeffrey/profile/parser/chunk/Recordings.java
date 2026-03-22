/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.parser.chunk;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.StandardOpenOption.*;

public abstract class Recordings {
    /**
     * Merges a list of JDK Flight Recorder files into a single output file.
     * This method concatenates all recording files in the order they appear in the list.
     * ! Does not work on Azure on mounted Filesystem !
     *
     * @param recordings List of paths to JFR recording files to be merged
     * @param outputPath Path where the merged recording file will be written
     * @throws RuntimeException if there's an error during the merge operation
     */
    public static Path mergeByStreaming(List<Path> recordings, Path outputPath) {
        try (FileChannel output = FileChannel.open(outputPath, CREATE, WRITE, TRUNCATE_EXISTING)) {
            for (Path recording : recordings) {
                try (FileChannel input = FileChannel.open(recording, READ)) {
                    long size = input.size();
                    long position = 0;
                    while (position < size) {
                        position += input.transferTo(position, size - position, output);
                    }
                }
            }
            output.force(true);
            return outputPath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to merge recordings: " + recordings, e);
        }
    }

    /**
     * Merges a list of JDK Flight Recorder files into a single output file.
     * This method concatenates all recording files in the order they appear in the list.
     *
     * @param inputs List of paths to JFR recording files to be merged
     * @param output Path where the merged recording file will be written
     * @throws RuntimeException if there's an error during the merge operation
     */
    public static void mergeByCopy(List<Path> inputs, Path output) {
        try {
            for (Path input : inputs) {
                Files.write(output, Files.readAllBytes(input), CREATE, APPEND);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot merge recordings to: " + output, e);
        }
    }

    /**
     * Merges multiple files into a single OutputStream using NIO Path and Files.copy
     * Note: This method does NOT close the OutputStream - caller is responsible for closing it
     *
     * @param inputs List of Path objects representing files to merge
     * @param stream OutputStream where merged content will be written
     */
    public static void mergeByStreaming(List<Path> inputs, OutputStream stream) {
        for (Path inputFile : inputs) {
            try {
                Files.copy(inputFile, stream);
            } catch (IOException e) {
                throw new RuntimeException("Cannot merge recordings to: " + inputFile, e);
            }
        }
    }

    /**
     * Copy a single file to an OutputStream using NIO Path and Files.copy.
     *
     * @param input  Single Path  representing file to copy
     * @param stream OutputStream where merged content will be written
     */
    public static void copyByStreaming(Path input, OutputStream stream) {
        mergeByStreaming(List.of(input), stream);
    }
}
