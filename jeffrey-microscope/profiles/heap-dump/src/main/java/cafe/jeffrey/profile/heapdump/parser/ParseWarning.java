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

import java.util.List;

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

    /** True if any warning in {@code warnings} has {@link Severity#ERROR}. */
    public static boolean anyError(List<ParseWarning> warnings) {
        for (ParseWarning w : warnings) {
            if (w.severity() == Severity.ERROR) {
                return true;
            }
        }
        return false;
    }
}
