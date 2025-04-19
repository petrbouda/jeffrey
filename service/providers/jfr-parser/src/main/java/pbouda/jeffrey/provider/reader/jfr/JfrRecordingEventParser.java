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
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.RecordingEventParser;
import pbouda.jeffrey.provider.api.model.IngestionContext;
import pbouda.jeffrey.provider.api.model.parser.ParserResult;
import pbouda.jeffrey.provider.api.model.parser.RecordingTypeSpecificData;
import pbouda.jeffrey.provider.reader.jfr.chunk.ChunkBasedRecordingDisassembler;
import pbouda.jeffrey.provider.reader.jfr.chunk.Recordings;
import pbouda.jeffrey.provider.reader.jfr.data.AutoAnalysisDataProvider;
import pbouda.jeffrey.provider.reader.jfr.data.JfrSpecificDataProvider;
import pbouda.jeffrey.tools.impl.jdk.JdkJfrTool;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

public class JfrRecordingEventParser implements RecordingEventParser {

    private static final Logger LOG = LoggerFactory.getLogger(JfrRecordingEventParser.class);

    private static final ChunkBasedRecordingDisassembler DISASSEMBLER =
            new ChunkBasedRecordingDisassembler(new JdkJfrTool());

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmssSSS");
    private final Path recordingsTempDir;


    public JfrRecordingEventParser(Path recordingsTempDir) {
        this.recordingsTempDir = recordingsTempDir;
    }

    private final List<JfrSpecificDataProvider> specificDataProviders =
            List.of(new AutoAnalysisDataProvider());


    @Override
    public ParserResult start(EventWriter eventWriter, IngestionContext context, Path recording) {
        String folderName = Instant.now().atZone(ZoneOffset.UTC).format(DATETIME_FORMATTER);
        Path profileTempFolder = this.recordingsTempDir.resolve(folderName);

        // Create a temporary folder for the recording while processing and remove it after the profile is created.
        // Temporary folder is used to parallelize the processing of multiple chunks of the processing.
        FileSystemUtils.createDirectories(profileTempFolder);
        LOG.info("Created the profile's temporary folder: {}", profileTempFolder);
        try {
            List<Path> recordingChunks = DISASSEMBLER.disassemble(recording, profileTempFolder);
            return _start(eventWriter, context, recordingChunks);
        } finally {
            FileSystemUtils.removeDirectory(profileTempFolder);
            LOG.info("Removed the profile's temporary folder: {}", profileTempFolder);
        }
    }

    private ParserResult _start(EventWriter eventWriter, IngestionContext context, List<Path> recordings) {
        Supplier<EventProcessor<Void>> eventProcessor = () -> {
            return new JfrEventReader(
                    eventWriter.newSingleThreadedWriter(),
                    context.eventFieldsSetting(),
                    context.profilingStart());
        };

        JdkRecordingIterators.parallelAndWait(recordings, eventProcessor);
        List<RecordingTypeSpecificData> recordingTypeSpecificData = specificDataProviders.stream()
                .map(provider -> provider.provide(recordings))
                .toList();

        return new ParserResult(recordingTypeSpecificData);
    }
}
