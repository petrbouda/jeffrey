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

package cafe.jeffrey.shared.volume.recording;

import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.volume.VolumeEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Optional;

/**
 * The instant async-profiler wrote into the file's name, read the same from its {@code .lz4}
 * archive: {@code profile-20260220-120500+0100.jfr}. A name outside the convention falls back to
 * the volume, logged at debug since this runs per file per listing.
 *
 * <p>async-profiler expands {@code %t} in the profiled machine's local time ({@code localtime_r}),
 * so the provisioner writes the machine's UTC offset right after it
 * ({@link JeffreyLayout#recordingTemplate}), and the name is read at that offset: read as UTC,
 * every chunk of a JVM running in Prague would claim to start an hour or two later than it did. A
 * name without an offset is read as UTC.
 */
final class RecordingNameTimestamp implements TimestampResolver {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingNameTimestamp.class);

    /** {@code 20260220-120500}, then the offset when the name carries one; UTC when it does not. */
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyyMMdd-HHmmss")
            .optionalStart()
            .appendOffset(JeffreyLayout.OFFSET_PATTERN, JeffreyLayout.UTC_OFFSET_TEXT)
            .optionalEnd()
            .parseDefaulting(ChronoField.OFFSET_SECONDS, ZoneOffset.UTC.getTotalSeconds())
            .toFormatter();
    private static final String PREFIX = "profile-";

    @Override
    public Optional<Instant> resolve(VolumeEntry file) {
        String name = file.name();
        // The id is the name without the recording's extension, the same for a chunk and its archive
        Optional<String> id = RecordingFormat.of(name).map(format -> format.idOf(name));

        if (id.isPresent() && id.get().startsWith(PREFIX)) {
            Optional<Instant> createdAt = parse(id.get().substring(PREFIX.length()));
            if (createdAt.isPresent()) {
                return createdAt;
            }
            LOG.debug("Recording has an unparseable timestamp in its name, asking the volume: filename={}",
                    name);
        } else {
            LOG.debug("Recording does not follow the naming convention, asking the volume: "
                    + "filename={} expected_prefix={}", name, PREFIX);
        }

        return VOLUME.resolve(file);
    }

    /** {@code 20260220-120500+0100} or {@code 20260220-120500}. */
    private static Optional<Instant> parse(String stamp) {
        try {
            return Optional.of(FORMATTER.parse(stamp, Instant::from));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
