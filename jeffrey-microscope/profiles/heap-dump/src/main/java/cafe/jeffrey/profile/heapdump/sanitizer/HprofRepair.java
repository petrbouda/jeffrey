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

package cafe.jeffrey.profile.heapdump.sanitizer;

/**
 * Low-level repair operation that can be applied to an HPROF file in-place.
 * Strategies emit these; {@link HprofInPlaceSanitizer} applies them.
 */
public sealed interface HprofRepair {

    /**
     * Overwrites the u4 body-length field of a top-level record header.
     *
     * @param lengthFieldOffset absolute offset of the length field (record start + 5)
     * @param newLength         new u4 body length (0 .. 0xFFFFFFFF)
     */
    record PatchRecordLength(long lengthFieldOffset, long newLength) implements HprofRepair {

        public PatchRecordLength {
            if (lengthFieldOffset < 0) {
                throw new IllegalArgumentException(
                        "lengthFieldOffset must be non-negative: lengthFieldOffset=" + lengthFieldOffset);
            }
            if (newLength < 0 || newLength > 0xFFFFFFFFL) {
                throw new IllegalArgumentException(
                        "newLength out of u4 range: newLength=" + newLength);
            }
        }
    }

    /**
     * Truncates the file at the given absolute offset (drops everything from this
     * offset onward).
     */
    record TruncateFile(long offset) implements HprofRepair {

        public TruncateFile {
            if (offset < 0) {
                throw new IllegalArgumentException("offset must be non-negative: offset=" + offset);
            }
        }
    }

    /**
     * Appends a synthetic 9-byte HEAP_DUMP_END record at the end of the file.
     */
    record AppendEndMarker() implements HprofRepair {
    }
}
