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

package pbouda.jeffrey.shared.folderqueue;

import pbouda.jeffrey.shared.common.IDGenerator;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Utility for generating and parsing folder queue filenames.
 * <p>
 * Format: {@code <yyyyMMddHHmmssSSS>_<uuid-v7-short>.json}
 * <p>
 * Example: {@code 20260220153045123_019505a1.json}
 * <p>
 * The timestamp prefix ensures lexicographic sort equals chronological order.
 * The UUID suffix (first 8 chars of a UUIDv7) prevents collisions between
 * concurrent CLI processes.
 */
public abstract class FolderQueueFilename {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);

    private static final int TIMESTAMP_LENGTH = 17;

    /**
     * Generates a sortable, collision-free filename.
     *
     * @param clock the clock to use for the timestamp
     * @return a filename like {@code 20260220153045123_019505a1.json}
     */
    public static String generate(Clock clock) {
        String timestamp = TIMESTAMP_FORMATTER.format(clock.instant());
        String uuid = IDGenerator.generate().substring(0, 8);
        return timestamp + "_" + uuid + ".json";
    }

    /**
     * Parses the UTC timestamp from a folder queue filename.
     *
     * @param filename the filename (e.g. {@code 20260220153045123_019505a1.json})
     * @return the instant represented by the timestamp prefix
     */
    public static Instant parseTimestamp(String filename) {
        String timestamp = filename.substring(0, TIMESTAMP_LENGTH);
        return TIMESTAMP_FORMATTER.parse(timestamp, Instant::from);
    }
}
