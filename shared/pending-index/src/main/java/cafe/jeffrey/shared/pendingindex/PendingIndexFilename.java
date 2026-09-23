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

package cafe.jeffrey.shared.pendingindex;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Generates pending index filenames.
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
}
