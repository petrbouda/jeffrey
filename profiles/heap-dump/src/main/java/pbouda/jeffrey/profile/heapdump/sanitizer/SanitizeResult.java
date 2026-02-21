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

package pbouda.jeffrey.profile.heapdump.sanitizer;

/**
 * Result of an HPROF sanitization operation. Contains metadata about what
 * corruption patterns were detected and fixed.
 *
 * @param wasModified              true if any fixes were applied to the file
 * @param hadZeroLengthSegments    true if zero-length HEAP_DUMP_SEGMENT records were found (Pattern 1)
 * @param wasTruncated             true if the last record was truncated (Pattern 2)
 * @param hadMissingEndMarker      true if the HEAP_DUMP_END record was missing (Pattern 3)
 * @param hadTruncatedSubRecords   true if truncated sub-records were found within segments (Pattern 4)
 * @param hadOverflowedLengths     true if negative/overflowed record lengths were found (Pattern 5)
 * @param zeroLengthSegmentsFixed  count of zero-length segments that were fixed
 * @param totalRecordsProcessed    total number of top-level records read
 * @param totalBytesRead           total bytes read from the input file
 * @param totalBytesWritten        total bytes written to the output file
 * @param estimatedObjectsRecovered number of sub-records recovered from fixed segments
 * @param summaryMessage           human-readable summary of the sanitization
 */
public record SanitizeResult(
        boolean wasModified,
        boolean hadZeroLengthSegments,
        boolean wasTruncated,
        boolean hadMissingEndMarker,
        boolean hadTruncatedSubRecords,
        boolean hadOverflowedLengths,
        int zeroLengthSegmentsFixed,
        long totalRecordsProcessed,
        long totalBytesRead,
        long totalBytesWritten,
        long estimatedObjectsRecovered,
        String summaryMessage
) {

    /**
     * Creates a result indicating no modifications were needed.
     */
    public static SanitizeResult unmodified(long totalRecords, long bytesRead, long bytesWritten) {
        return new SanitizeResult(
                false, false, false, false, false, false,
                0, totalRecords, bytesRead, bytesWritten, 0,
                "HPROF file is valid, no sanitization needed"
        );
    }

    /**
     * Builder to accumulate fixes during sanitization.
     */
    public static class Builder {
        private boolean hadZeroLengthSegments;
        private boolean wasTruncated;
        private boolean hadMissingEndMarker;
        private boolean hadTruncatedSubRecords;
        private boolean hadOverflowedLengths;
        private int zeroLengthSegmentsFixed;
        private long totalRecordsProcessed;
        private long totalBytesRead;
        private long totalBytesWritten;
        private long estimatedObjectsRecovered;

        public Builder zeroLengthSegmentFixed(long recoveredSubRecords) {
            this.hadZeroLengthSegments = true;
            this.zeroLengthSegmentsFixed++;
            this.estimatedObjectsRecovered += recoveredSubRecords;
            return this;
        }

        public Builder truncated() {
            this.wasTruncated = true;
            return this;
        }

        public Builder missingEndMarker() {
            this.hadMissingEndMarker = true;
            return this;
        }

        public Builder truncatedSubRecords(long recoveredSubRecords) {
            this.hadTruncatedSubRecords = true;
            this.estimatedObjectsRecovered += recoveredSubRecords;
            return this;
        }

        public Builder overflowedLength(long recoveredSubRecords) {
            this.hadOverflowedLengths = true;
            this.estimatedObjectsRecovered += recoveredSubRecords;
            return this;
        }

        public Builder totalRecordsProcessed(long count) {
            this.totalRecordsProcessed = count;
            return this;
        }

        public Builder totalBytesRead(long bytes) {
            this.totalBytesRead = bytes;
            return this;
        }

        public Builder totalBytesWritten(long bytes) {
            this.totalBytesWritten = bytes;
            return this;
        }

        public SanitizeResult build() {
            boolean wasModified = hadZeroLengthSegments || wasTruncated
                    || hadMissingEndMarker || hadTruncatedSubRecords || hadOverflowedLengths;

            StringBuilder summary = new StringBuilder();
            if (!wasModified) {
                summary.append("HPROF file is valid, no sanitization needed");
            } else {
                summary.append("HPROF file sanitized:");
                if (hadZeroLengthSegments) {
                    summary.append(" fixed ").append(zeroLengthSegmentsFixed).append(" zero-length segment(s);");
                }
                if (hadOverflowedLengths) {
                    summary.append(" fixed overflowed record length(s);");
                }
                if (hadTruncatedSubRecords) {
                    summary.append(" trimmed truncated sub-record(s);");
                }
                if (wasTruncated) {
                    summary.append(" discarded truncated last record;");
                }
                if (hadMissingEndMarker) {
                    summary.append(" appended HEAP_DUMP_END marker;");
                }
                if (estimatedObjectsRecovered > 0) {
                    summary.append(" recovered ~").append(estimatedObjectsRecovered).append(" sub-record(s)");
                }
            }

            return new SanitizeResult(
                    wasModified,
                    hadZeroLengthSegments,
                    wasTruncated,
                    hadMissingEndMarker,
                    hadTruncatedSubRecords,
                    hadOverflowedLengths,
                    zeroLengthSegmentsFixed,
                    totalRecordsProcessed,
                    totalBytesRead,
                    totalBytesWritten,
                    estimatedObjectsRecovered,
                    summary.toString()
            );
        }
    }
}
