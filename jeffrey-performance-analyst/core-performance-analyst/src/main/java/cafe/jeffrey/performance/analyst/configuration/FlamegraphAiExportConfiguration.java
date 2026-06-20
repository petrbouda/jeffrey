/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.performance.analyst.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.performance.analyst.flamegraph.RecordingFlamegraphAiExporter;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Wiring for the in-memory JFR → Frame → AI-prompt flamegraph export.
 */
@Configuration
public class FlamegraphAiExportConfiguration {

    private static final String HOME_DIR =
            "${jeffrey.performance-analyst.home.dir:${user.home}/.jeffrey-performance-analyst}";
    private static final String TEMP_SUBDIR = "temp";

    @Bean
    public RecordingFlamegraphAiExporter recordingFlamegraphAiExporter(@Value(HOME_DIR) String homeDir) {
        Path tempBase = Path.of(homeDir).resolve(TEMP_SUBDIR);
        try {
            Files.createDirectories(tempBase);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create temp directory: " + tempBase, e);
        }

        TempDirFactory tempDirFactory = TempDirFactory.of(tempBase);
        Lz4Compressor lz4Compressor = new Lz4Compressor(tempDirFactory);
        return new RecordingFlamegraphAiExporter(tempDirFactory, lz4Compressor);
    }
}
