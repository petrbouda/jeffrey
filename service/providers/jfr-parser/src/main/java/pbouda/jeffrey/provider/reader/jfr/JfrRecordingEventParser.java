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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.compression.Lz4Compressor;
import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.common.filesystem.JeffreyDirs.Directory;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.RecordingEventParser;
import pbouda.jeffrey.provider.api.model.parser.ParserResult;
import pbouda.jeffrey.provider.api.model.parser.RecordingTypeSpecificData;
import pbouda.jeffrey.provider.reader.jfr.chunk.Recordings;
import pbouda.jeffrey.provider.reader.jfr.data.AutoAnalysisDataProvider;
import pbouda.jeffrey.provider.reader.jfr.data.JfrSpecificDataProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class JfrRecordingEventParser implements RecordingEventParser {

    private static final Logger LOG = LoggerFactory.getLogger(JfrRecordingEventParser.class);

    private final JeffreyDirs jeffreyDirs;
    private final Lz4Compressor lz4Compressor;

    public JfrRecordingEventParser(JeffreyDirs jeffreyDirs, Lz4Compressor lz4Compressor) {
        this.jeffreyDirs = jeffreyDirs;
        this.lz4Compressor = lz4Compressor;
    }

    private final List<JfrSpecificDataProvider> specificDataProviders =
            List.of(new AutoAnalysisDataProvider());


    @Override
    public ParserResult start(EventWriter eventWriter, Path recording) {
        try (Directory tempDir = jeffreyDirs.newTempDir()) {
            LOG.info("Created the profile's temporary folder: {}", tempDir.path());

            Path decompressed = Lz4Compressor.isLz4Compressed(recording)
                    ? lz4Compressor.decompressToDir(recording, tempDir.path())
                    : recording;

            List<Path> recordingChunks = Recordings.splitRecording(decompressed, tempDir.path().resolve("chunks"));
            return _start(eventWriter, recordingChunks);
        }
    }

    private ParserResult _start(EventWriter eventWriter, List<Path> recordings) {
        Supplier<EventProcessor<Void>> eventProcessor = () -> {
            return new JfrEventReader(eventWriter.newSingleThreadedWriter());
        };

        JdkRecordingIterators.parallelAndWait(recordings, eventProcessor);
        List<RecordingTypeSpecificData> recordingTypeSpecificData = specificDataProviders.stream()
                .map(provider -> provider.provide(recordings))
                .toList();

        return new ParserResult(recordingTypeSpecificData);
    }
}
