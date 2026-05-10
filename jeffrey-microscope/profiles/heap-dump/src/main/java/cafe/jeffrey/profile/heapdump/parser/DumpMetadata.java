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
        long parsedAtMs) {
}
