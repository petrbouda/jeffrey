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
