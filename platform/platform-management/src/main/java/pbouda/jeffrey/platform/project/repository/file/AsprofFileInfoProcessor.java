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

package pbouda.jeffrey.platform.project.repository.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;

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

    private static final SupportedRecordingFile DEFAULT_FILE = SupportedRecordingFile.JFR;
    private static final int EXTENSION_LENGTH = DEFAULT_FILE.fileExtension().length() + 1;

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

    @Override
    public Instant createdAt(Path file) {
        Path filename = file.getFileName();
        String filenameStr = filename.toString();

        if (DEFAULT_FILE.matches(filename)) {
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
        String name = filename.toString();
        return name.substring(filePrefix.length(), name.length() - EXTENSION_LENGTH);
    }

    private static Instant parseToInstant(String timestamp) {
        LocalDateTime localDateTime = LocalDateTime.parse(timestamp, FORMATTER);
        return localDateTime.toInstant(ZoneOffset.UTC);
    }
}
