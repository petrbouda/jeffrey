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
 * Sealed marker for HPROF records.
 *
 * Records are produced by the streaming parser and consumed by the index builder.
 * Concrete subtypes are introduced as the parser grows; only the framing record
 * types needed by the foundation skeleton are present here.
 *
 * Top-level records (between the file header and EOF) implement {@link Top}.
 * Sub-records (inside HEAP_DUMP / HEAP_DUMP_SEGMENT bodies) implement {@link Sub}.
 */
public sealed interface HprofRecord {

    /** Offset within the .hprof file at which this record's body begins. */
    long fileOffset();

    sealed interface Top extends HprofRecord {
    }

    sealed interface Sub extends HprofRecord {
    }

    /**
     * Marks a top-level HEAP_DUMP or HEAP_DUMP_SEGMENT region. Body bytes are not
     * materialised — the index builder iterates sub-records directly from the file.
     */
    record HeapDumpRegion(long fileOffset, int byteLength, boolean isSegment) implements Top {
        public HeapDumpRegion {
            if (byteLength < 0) {
                throw new IllegalArgumentException("byteLength must be non-negative: byteLength=" + byteLength);
            }
        }
    }

    /** A top-level record whose tag is recognised but whose body is not yet decoded. */
    record OpaqueTop(int tag, long fileOffset, int byteLength) implements Top {
        public OpaqueTop {
            if (byteLength < 0) {
                throw new IllegalArgumentException("byteLength must be non-negative: byteLength=" + byteLength);
            }
        }
    }

    /** A sub-record whose tag is recognised but whose body is not yet decoded. */
    record OpaqueSub(int tag, long fileOffset, int byteLength) implements Sub {
        public OpaqueSub {
            if (byteLength < 0) {
                throw new IllegalArgumentException("byteLength must be non-negative: byteLength=" + byteLength);
            }
        }
    }
}
