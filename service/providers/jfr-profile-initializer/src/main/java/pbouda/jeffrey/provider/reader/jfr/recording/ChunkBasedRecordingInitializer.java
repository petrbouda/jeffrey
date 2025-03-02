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

package pbouda.jeffrey.provider.reader.jfr.recording;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.JfrFileUtils;
import pbouda.jeffrey.tools.api.JfrTool;

import java.nio.file.Path;
import java.util.List;

public class ChunkBasedRecordingInitializer implements RecordingInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ChunkBasedRecordingInitializer.class);

    private final JfrTool jfrTool;
    private final RecordingInitializer fallbackRecordingInitializer;

    public ChunkBasedRecordingInitializer(
            JfrTool jfrTool,
            RecordingInitializer fallbackRecordingInitializer) {

        this.jfrTool = jfrTool;
        this.fallbackRecordingInitializer = fallbackRecordingInitializer;
    }

    @Override
    public List<Path> initialize(Path recordingsDir, Path sourceRecording) {
        try {
            jfrTool.disassemble(sourceRecording, recordingsDir);
        } catch (Exception e) {
            LOG.info("Cannot disassemble using ChunkBasedRecordingInitializer, " +
                            "fallback to SingleFileRecordingInitializer: source={}, recordings_dir={} error={}",
                    sourceRecording, recordingsDir, e.getMessage());

            return fallbackRecordingInitializer.initialize(recordingsDir, sourceRecording);
        }

        return JfrFileUtils.listJfrFiles(recordingsDir);
    }
}
