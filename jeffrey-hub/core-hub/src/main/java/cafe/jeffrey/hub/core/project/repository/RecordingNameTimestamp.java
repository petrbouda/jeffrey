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

package cafe.jeffrey.hub.core.project.repository;

import cafe.jeffrey.shared.common.model.repository.FileExtensions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * The instant async-profiler wrote into the file's own name — {@code profile-20260220-120500.jfr}.
 *
 * <p>The only timestamp that survives the hub rewriting the file, which is why a JFR uses it and
 * why compressing one is allowed at all: {@code profile-20260220-120500.jfr.lz4} still answers
 * with the moment the profiler opened the chunk, where its creation time would answer with the
 * moment the compression job ran. Compression is undone before the name is read for exactly that
 * reason.
 *
 * <p>Anything the convention does not cover falls back to the filesystem — another prefix, a
 * timestamp that does not parse. A failure must not propagate: the caller drops a file it cannot
 * describe, so one oddly named file would leave the listing entirely rather than sort imprecisely.
 *
 * <p>Said at debug rather than warned about, because this is asked once per file per listing: a
 * repository whose chunks are named otherwise is a supported arrangement that merely sorts by the
 * filesystem, and warning about it would put a line in the log for every file on every page load
 * and every run of every scheduled job.
 */
final class RecordingNameTimestamp implements TimestampResolver {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingNameTimestamp.class);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String PREFIX = "profile-";
    private static final String RECORDING_SUFFIX = "." + FileExtensions.JFR;
    private static final String COMPRESSED_SUFFIX = "." + FileExtensions.LZ4;

    @Override
    public Instant resolve(Path file) {
        String name = uncompressed(file.getFileName().toString());

        if (name.startsWith(PREFIX) && name.endsWith(RECORDING_SUFFIX)) {
            Instant createdAt = parse(name.substring(PREFIX.length(), name.length() - RECORDING_SUFFIX.length()));
            if (createdAt != null) {
                return createdAt;
            }
            LOG.debug("Recording has an unparseable timestamp in its name, asking the filesystem: filename={}",
                    name);
        } else {
            LOG.debug("Recording does not follow the naming convention, asking the filesystem: "
                    + "filename={} expected_prefix={}", name, PREFIX);
        }

        return FILESYSTEM.resolve(file);
    }

    private static String uncompressed(String filename) {
        if (filename.endsWith(COMPRESSED_SUFFIX)) {
            return filename.substring(0, filename.length() - COMPRESSED_SUFFIX.length());
        }
        return filename;
    }

    private static Instant parse(String timestamp) {
        try {
            return LocalDateTime.parse(timestamp, FORMATTER).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
