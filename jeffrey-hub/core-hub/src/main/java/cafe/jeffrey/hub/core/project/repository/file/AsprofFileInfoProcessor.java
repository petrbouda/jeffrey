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

package cafe.jeffrey.hub.core.project.repository.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.repository.SupportedFile;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

public class AsprofFileInfoProcessor implements FileInfoProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AsprofFileInfoProcessor.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String DEFAULT_PREFIX = "profile-";

    private final FileInfoProcessor fallbackProcessor;
    private final String filePrefix;

    public AsprofFileInfoProcessor() {
        this(new FilesystemFileInfoProcessor(), DEFAULT_PREFIX);
    }

    public AsprofFileInfoProcessor(String filePrefix) {
        this(new FilesystemFileInfoProcessor(), filePrefix);
    }

    public AsprofFileInfoProcessor(FileInfoProcessor fallbackProcessor, String filePrefix) {
        this.fallbackProcessor = fallbackProcessor;
        this.filePrefix = filePrefix;
    }


    @Override
    public Comparator<Path> comparator() {
        return Comparator.comparing((Path f) -> f.getFileName().toString()).reversed();
    }

    /**
     * A chunk is dated by the timestamp in its name, whichever form it is in: the hub renames a
     * chunk to {@code .jfr.lz4} when it compresses it, and dating that by the file system would
     * date it by the compression rather than the recording — after every chunk still raw, which
     * turns the order a recording is read in upside down.
     */
    @Override
    public Instant createdAt(Path file) {
        Path filename = file.getFileName();
        String filenameStr = filename.toString();

        if (SupportedFile.of(filename).isRecordingChunk()) {
            if (filenameStr.startsWith(filePrefix)) {
                String timestamp = extractTimestamp(filename);
                return parseToInstant(timestamp);
            }

            LOG.warn("JFR File has unsupported name convention: filename={} supported_prefix={}",
                    filename, filePrefix);
        }

        return fallbackProcessor.createdAt(file);
    }

    private String extractTimestamp(Path filename) {
        String name = FileSystemUtils.removeExtension(filename, SupportedFile.recordingChunkExtensions());
        return name.substring(filePrefix.length());
    }

    private static Instant parseToInstant(String timestamp) {
        LocalDateTime localDateTime = LocalDateTime.parse(timestamp, FORMATTER);
        return localDateTime.toInstant(ZoneOffset.UTC);
    }
}
