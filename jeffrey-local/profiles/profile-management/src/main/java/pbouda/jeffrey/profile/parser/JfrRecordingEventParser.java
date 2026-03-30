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

package pbouda.jeffrey.profile.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.profile.parser.chunk.JfrParser;
import pbouda.jeffrey.provider.profile.EventWriter;
import pbouda.jeffrey.provider.profile.RecordingEventParser;
import pbouda.jeffrey.shared.common.compression.Lz4Compressor;
import pbouda.jeffrey.shared.common.filesystem.TempDirFactory;
import pbouda.jeffrey.shared.common.filesystem.TempDirectory;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class JfrRecordingEventParser implements RecordingEventParser {

    private static final Logger LOG = LoggerFactory.getLogger(JfrRecordingEventParser.class);

    private final TempDirFactory tempDirFactory;
    private final Lz4Compressor lz4Compressor;

    public JfrRecordingEventParser(TempDirFactory tempDirFactory, Lz4Compressor lz4Compressor) {
        this.tempDirFactory = tempDirFactory;
        this.lz4Compressor = lz4Compressor;
    }

    @Override
    public void start(EventWriter eventWriter, Path recording) {
        try (TempDirectory tempDir = tempDirFactory.newTempDir()) {
            LOG.info("Created the profile's temporary folder: {}", tempDir.path());

            Path decompressed = Lz4Compressor.isLz4Compressed(recording)
                    ? lz4Compressor.decompressToDir(recording, tempDir.path())
                    : recording;

            List<Path> recordingChunks = JfrParser.disassemble(decompressed, tempDir.path().resolve("chunks"));
            Supplier<EventProcessor<Void>> eventProcessor =
                    () -> new JfrEventReader(eventWriter.newSingleThreadedWriter());

            JdkRecordingIterators.parallelAndWait(recordingChunks, eventProcessor);
        }
    }
}
