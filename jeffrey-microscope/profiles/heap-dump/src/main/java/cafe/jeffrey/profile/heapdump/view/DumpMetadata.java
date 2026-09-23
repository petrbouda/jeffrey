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
package cafe.jeffrey.profile.heapdump.view;

/**
 * The single row of the {@code dump_metadata} table.
 *
 * Surfaces parse health (truncated, warning_count) and provenance
 * (parser_version, parsed_at_ms) so the UI can decide whether the index
 * needs rebuilding without reading the .hprof file directly.
 */
public record DumpMetadata(
        String hprofPath,
        long hprofSizeBytes,
        long hprofMtimeMs,
        int idSize,
        String hprofVersion,
        long timestampMs,
        long bytesParsed,
        long recordCount,
        long warningCount,
        boolean truncated,
        String parserVersion,
        long parsedAtMs,
        boolean compressedOops) {
}
