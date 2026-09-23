/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core.project.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.function.Supplier;
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
    private static final String RECORDING_SUFFIX = ".jfr";
    private static final String COMPRESSED_SUFFIX = ".lz4";

    @Override
    public Instant resolve(Path file, Supplier<BasicFileAttributes> attributes) {
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

        return FILESYSTEM.resolve(file, attributes);
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
