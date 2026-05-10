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
 * A non-fatal anomaly emitted by the parser. Persisted to the
 * {@code parse_warning} table for forensics; counted in
 * {@code dump_metadata.warning_count}.
 *
 * @param fileOffset offset within the .hprof where the issue was detected
 * @param recordKind raw HPROF tag byte, or {@code null} when the warning is
 *                   not specific to a record (e.g. truncated header)
 * @param severity   informational, recoverable warning, or unrecoverable error
 * @param message    human-readable explanation
 */
public record ParseWarning(long fileOffset, Integer recordKind, Severity severity, String message) {

    public ParseWarning {
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("message must not be null or empty");
        }
    }

    /** Severity ordinal matches the on-disk TINYINT in the parse_warning table. */
    public enum Severity {
        INFO, WARN, ERROR
    }
}
