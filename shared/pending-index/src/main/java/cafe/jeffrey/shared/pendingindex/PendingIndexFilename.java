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

package cafe.jeffrey.shared.pendingindex;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Generates and parses pending index filenames.
 * <p>
 * Format: {@code <yyyyMMddHHmmssSSS>_<id>}
 * <p>
 * Example: {@code 20260220153045123_019505a1-2b3c-7def-8abc-1234567890ab}
 * <p>
 * The timestamp prefix makes lexicographic sort equal chronological order, so a reader
 * processes entries in the order they were declared. The caller-provided id suffix (a UUIDv7
 * in practice) keeps concurrent writers from colliding inside the same millisecond.
 */
public abstract class PendingIndexFilename {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);

    private static final int TIMESTAMP_LENGTH = 17;

    /**
     * Generates a sortable, collision-free filename.
     *
     * @param clock the clock to use for the timestamp
     * @param id    the caller-provided identifier to embed in the filename
     * @return a filename like {@code 20260220153045123_019505a1-2b3c-7def-8abc-1234567890ab}
     */
    public static String generate(Clock clock, String id) {
        String timestamp = TIMESTAMP_FORMATTER.format(clock.instant());
        return timestamp + "_" + id;
    }

    /**
     * Parses the UTC timestamp from a pending index filename.
     *
     * @param filename the filename (e.g. {@code 20260220153045123_019505a1})
     * @return the instant represented by the timestamp prefix
     */
    public static Instant parseTimestamp(String filename) {
        String timestamp = filename.substring(0, TIMESTAMP_LENGTH);
        return TIMESTAMP_FORMATTER.parse(timestamp, Instant::from);
    }
}
