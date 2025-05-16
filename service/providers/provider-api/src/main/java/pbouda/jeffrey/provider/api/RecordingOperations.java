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

package pbouda.jeffrey.provider.api;

import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Operations related to JDK Flight Recorder files manipulation.
 */
public interface RecordingOperations {

    /**
     * Merges a list of JFR recording files into a single output file.
     *
     * @param recordings List of recording files to merge
     * @param outputPath Path to the output merged file
     */
    void mergeRecordings(List<Path> recordings, Path outputPath);

    /**
     * Merges a list of JFR recording files into a single output in the form of a {@link FileChannel}.
     *
     * @param recordings List of recording files to merge
     * @return FileChannel representing the merged recording file
     */
    FileChannel mergeRecordings(List<Path> recordings);

    /**
     * Merges a list of JFR recording files and automatically consumes the input stream.
     *
     * @param recordings List of recording files to merge
     * @param consumer   Consumer that takes a OutputStream to handle the merged recording
     */
    void mergeRecordingsWithStreamConsumer(List<Path> recordings, Consumer<InputStream> consumer);

    /**
     * Splits a JFR recording file into multiple parts and writes each part to the specified output directory.
     *
     * @param recording the path to the input recording file to be split
     * @param outputDir the path to the output directory where the split recordings will be saved
     */
    void splitRecording(Path recording, Path outputDir);
}
