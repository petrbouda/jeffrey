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
import cafe.jeffrey.shared.common.model.repository.FileExtensions;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;

public class AsprofFileInfoProcessor implements FileInfoProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AsprofFileInfoProcessor.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String DEFAULT_PREFIX = "profile-";

    private static final SupportedRecordingFile DEFAULT_FILE = SupportedRecordingFile.JFR;
    private static final int EXTENSION_LENGTH = DEFAULT_FILE.fileExtension().length() + 1;
    private static final String COMPRESSED_SUFFIX = "." + FileExtensions.LZ4;

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
     * When the profiler opened the chunk, read out of the name it gave the file.
     *
     * <p>Compression is undone before the name is read. The hub rewrites a closed chunk as
     * {@code <name>.jfr.lz4}, and the archive is a new file with a new creation time: reading the
     * filesystem for it would report when the hub compressed it, minutes to hours after the
     * profiler wrote it, and out of order with the chunks still uncompressed beside it. That
     * timestamp is what orders the session — {@code ChunkWindow} tiles the recording with it,
     * both retention jobs age files by it, and which chunk is still open is a maximum over it —
     * so a file's own name is the only source that survives the hub touching it.
     *
     * <p>Anything the convention does not cover falls back to the filesystem: another prefix,
     * another format, a timestamp that does not parse. A name that fails to parse must not
     * propagate as a failure — the caller drops a file it cannot describe, so a single oddly
     * named file would disappear from the listing rather than sort imprecisely.
     */
    @Override
    public Instant createdAt(Path file) {
        String filename = uncompressedName(file.getFileName().toString());

        if (DEFAULT_FILE.matches(filename)) {
            if (filename.startsWith(filePrefix)) {
                Instant createdAt = parseToInstant(extractTimestamp(filename));
                if (createdAt != null) {
                    return createdAt;
                }
                LOG.warn("JFR file has an unparseable timestamp, falling back to the filesystem: filename={}",
                        filename);
            } else {
                LOG.warn("JFR File has unsupported name convention: filename={} supported_prefix={}",
                        filename, filePrefix);
            }
        }

        return fallbackProcessor.createdAt(file);
    }

    private static String uncompressedName(String filename) {
        if (filename.endsWith(COMPRESSED_SUFFIX)) {
            return filename.substring(0, filename.length() - COMPRESSED_SUFFIX.length());
        }
        return filename;
    }

    private String extractTimestamp(String filename) {
        return filename.substring(filePrefix.length(), filename.length() - EXTENSION_LENGTH);
    }

    private static Instant parseToInstant(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp, FORMATTER).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
