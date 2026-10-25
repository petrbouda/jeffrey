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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * The instant async-profiler wrote into the file's name ({@code profile-20260220-120500.jfr}), read
 * the same from its {@code .lz4} archive. A name outside the convention falls back to the
 * filesystem, logged at debug since this runs per file per listing.
 *
 * <p>async-profiler expands {@code %t} in the profiled machine's local time ({@code localtime_r}),
 * so the name is read in the zone the session recorded, not in UTC: read as UTC, every chunk of a
 * JVM running in Prague would claim to start an hour or two later than it did.
 */
final class RecordingNameTimestamp implements TimestampResolver {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingNameTimestamp.class);

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String PREFIX = "profile-";
    private static final String RECORDING_SUFFIX = ".jfr";
    private static final String COMPRESSED_SUFFIX = ".lz4";

    @Override
    public Instant resolve(Path file, ZoneId zone, Supplier<BasicFileAttributes> attributes) {
        String name = uncompressed(file.getFileName().toString());

        if (name.startsWith(PREFIX) && name.endsWith(RECORDING_SUFFIX)) {
            String timestamp = name.substring(PREFIX.length(), name.length() - RECORDING_SUFFIX.length());
            Instant createdAt = parse(timestamp, zone);
            if (createdAt != null) {
                return createdAt;
            }
            LOG.debug("Recording has an unparseable timestamp in its name, asking the filesystem: filename={}",
                    name);
        } else {
            LOG.debug("Recording does not follow the naming convention, asking the filesystem: "
                    + "filename={} expected_prefix={}", name, PREFIX);
        }

        return FILESYSTEM.resolve(file, zone, attributes);
    }

    private static String uncompressed(String filename) {
        if (filename.endsWith(COMPRESSED_SUFFIX)) {
            return filename.substring(0, filename.length() - COMPRESSED_SUFFIX.length());
        }
        return filename;
    }

    private static Instant parse(String timestamp, ZoneId zone) {
        try {
            return LocalDateTime.parse(timestamp, FORMATTER).atZone(zone).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
