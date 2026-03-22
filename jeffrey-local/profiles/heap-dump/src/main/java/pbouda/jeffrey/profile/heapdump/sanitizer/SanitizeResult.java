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
 * Result of sanitizing an HPROF heap dump file.
 *
 * @param wasModified               true if any repairs were made
 * @param hadZeroLengthSegments     true if zero-length HEAP_DUMP_SEGMENT records were found
 * @param wasTruncated              true if the file was truncated mid-record
 * @param hadMissingEndMarker       true if the HEAP_DUMP_END record was missing
 * @param hadTruncatedSubRecords    true if sub-records within segments were truncated
 * @param hadOverflowedLengths      true if segment lengths exceeded remaining file size
 * @param zeroLengthSegmentsFixed   number of zero-length segments that were rescanned
 * @param totalRecordsProcessed     total number of top-level records processed
 * @param totalBytesRead            total bytes read from input
 * @param totalBytesWritten         total bytes written to output
 * @param estimatedObjectsRecovered estimated number of objects recovered from truncated segments
 * @param summaryMessage            human-readable summary of repairs
 */
public record SanitizeResult(
        boolean wasModified,
        boolean hadZeroLengthSegments,
        boolean wasTruncated,
        boolean hadMissingEndMarker,
        boolean hadTruncatedSubRecords,
        boolean hadOverflowedLengths,
        int zeroLengthSegmentsFixed,
        int totalRecordsProcessed,
        long totalBytesRead,
        long totalBytesWritten,
        long estimatedObjectsRecovered,
        String summaryMessage
) {

    /**
     * Creates a result indicating no modifications were needed.
     */
    public static SanitizeResult unmodified(int totalRecords, long bytesRead, long bytesWritten) {
        return new SanitizeResult(
                false, false, false, false, false, false,
                0, totalRecords, bytesRead, bytesWritten, 0,
                "No repairs needed"
        );
    }
}
