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
package cafe.jeffrey.profile.heapdump.parser;

/**
 * Decoded HPROF file header.
 *
 * The on-disk layout is: a NUL-terminated magic ASCII string, a 4-byte ID size,
 * and an 8-byte big-endian timestamp (millis since epoch). {@code headerSize}
 * is the offset of the first top-level record.
 */
public record HprofHeader(
        String version,
        int idSize,
        long timestampMs,
        long headerSize) {

    public static final String MAGIC_1_0_1 = "JAVA PROFILE 1.0.1";
    public static final String MAGIC_1_0_2 = "JAVA PROFILE 1.0.2";
    public static final String MAGIC_1_0_3 = "JAVA PROFILE 1.0.3";

    public HprofHeader {
        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        if (idSize != 4 && idSize != 8) {
            throw new IllegalArgumentException("idSize must be 4 or 8: idSize=" + idSize);
        }
        if (headerSize <= 0) {
            throw new IllegalArgumentException("headerSize must be positive: headerSize=" + headerSize);
        }
    }
}
