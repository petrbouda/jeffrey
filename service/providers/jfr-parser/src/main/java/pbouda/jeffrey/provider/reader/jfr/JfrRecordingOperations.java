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

package pbouda.jeffrey.provider.reader.jfr;

import pbouda.jeffrey.provider.api.RecordingOperations;
import pbouda.jeffrey.provider.reader.jfr.chunk.Recordings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class JfrRecordingOperations implements RecordingOperations {

    @Override
    public void mergeRecordings(List<Path> recordings, Path outputPath) {
        Recordings.mergeRecordings(recordings, outputPath);
    }

    @Override
    public FileChannel mergeRecordings(List<Path> recordings) {
        return null;
    }

    @Override
    public void mergeRecordingsWithStreamConsumer(List<Path> recordings, Consumer<InputStream> consumer) {
        for (Path recording : recordings) {
            try (InputStream inputStream = Files.newInputStream(recording)) {
                consumer.accept(inputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void splitRecording(Path recording, Path outputDir) {
        Recordings.splitRecording(recording, outputDir);
    }
}
