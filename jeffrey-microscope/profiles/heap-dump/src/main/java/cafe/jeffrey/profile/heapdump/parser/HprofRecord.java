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
 * Records are produced by the streaming parser ({@link HprofTopLevelReader} for
 * top-level records, {@link HprofSubRecordReader} for sub-records inside heap
 * dump segments) and consumed by the index builder.
 *
 * Top-level records implement {@link Top}; sub-records implement {@link Sub}.
 *
 * Records reference their location in the source file via {@link #fileOffset()}
 * so the index builder can re-read them later without buffering payload bytes.
 */
public sealed interface HprofRecord {

    /** Offset within the .hprof file at which this record's body begins. */
    long fileOffset();

    sealed interface Top extends HprofRecord {
    }

    sealed interface Sub extends HprofRecord {
    }

    // ---- Top-level records ------------------------------------------------

    /**
     * STRING (tag 0x01): a UTF-8 string entry in the HPROF string pool.
     * Referenced by class names and field names via {@code stringId}.
     */
    record HprofString(long stringId, byte[] utf8, long fileOffset) implements Top {
    }

    /**
     * LOAD_CLASS (tag 0x02): announces a class with its serial number and the
     * string ID of its (typically slash-delimited) name.
     */
    record LoadClass(int classSerial, long classId, int traceSerial, long nameStringId,
                     long fileOffset) implements Top {
    }

    /**
     * Marks a top-level HEAP_DUMP or HEAP_DUMP_SEGMENT region. Body bytes are not
     * materialised — the index builder iterates sub-records directly from the file.
     */
    record HeapDumpRegion(long fileOffset, long byteLength, boolean isSegment) implements Top {
        public HeapDumpRegion {
            if (byteLength < 0) {
                throw new IllegalArgumentException("byteLength must be non-negative: byteLength=" + byteLength);
            }
        }
    }

    /** A top-level record whose tag is recognised but whose body is not yet decoded. */
    record OpaqueTop(int tag, long fileOffset, long byteLength) implements Top {
        public OpaqueTop {
            if (byteLength < 0) {
                throw new IllegalArgumentException("byteLength must be non-negative: byteLength=" + byteLength);
            }
        }
    }

    /**
     * STACK_FRAME (tag 0x04): one stack frame, identified by {@code stackFrameId}.
     * String ids resolve via the HPROF string pool. {@code classSerial} joins to
     * the LOAD_CLASS {@code classSerial}. {@code lineNumber} is the raw HPROF
     * value: {@code &gt;= 1} normal, {@code -1} no info, {@code -2} compiled,
     * {@code -3} native.
     */
    record StackFrame(long stackFrameId, long methodNameStringId, long methodSignatureStringId,
                      long sourceFileNameStringId, int classSerial, int lineNumber,
                      long fileOffset) implements Top {
    }

    /**
     * STACK_TRACE (tag 0x05): an ordered list of stack frame ids belonging to a
     * single thread. {@code threadSerial} links to ROOT_THREAD_OBJECT's
     * {@code threadSerial}; the same value also annotates ROOT_JAVA_FRAME so
     * locals can be attributed back to their frame.
     */
    record StackTrace(int traceSerial, int threadSerial, long[] frameIds,
                      long fileOffset) implements Top {
    }

    // ---- Sub-records (inside HEAP_DUMP / HEAP_DUMP_SEGMENT) ---------------

    /**
     * A GC root (one of the ROOT_* sub-records).
     * {@code rootKind} is the raw HPROF sub-tag byte. Optional fields
     * ({@code threadSerial}, {@code frameIndex}) are -1 when not applicable
     * to the kind.
     */
    record GcRoot(int rootKind, long instanceId, int threadSerial, int frameIndex,
                  long fileOffset) implements Sub {
    }

    /**
     * CLASS_DUMP (tag 0x20): one row in the heap's class metadata. Field
     * descriptors are not eagerly decoded — only the byte length needed for
     * shallow size computation is captured ({@code instanceFieldsByteLength}).
     *
     * {@code instanceFieldTypes} carries the HPROF basic-type byte for each
     * instance field in declaration order; the index uses it together with the
     * super-class chain to walk INSTANCE_DUMP field bytes for outbound-reference
     * extraction.
     */
    record ClassDump(long classId, int traceSerial, long superClassId, long classloaderId,
                     long signersId, long protectionDomainId, int instanceSize,
                     int instanceFieldsByteLength, int totalByteLength,
                     long[] instanceFieldNameIds, int[] instanceFieldTypes,
                     long fileOffset) implements Sub {
    }

    /**
     * INSTANCE_DUMP (tag 0x21): a regular object. {@code instanceFieldsByteLength}
     * is the size of the encoded field-values block following the header.
     */
    record InstanceDump(long instanceId, int traceSerial, long classId,
                        int instanceFieldsByteLength, long fileOffset) implements Sub {
    }

    /**
     * OBJECT_ARRAY_DUMP (tag 0x22): an Object[] (array of references).
     * {@code length} is the element count; {@code arrayClassId} is the class
     * object id of the array type (e.g. {@code java.lang.String[]}).
     */
    record ObjectArrayDump(long arrayId, int traceSerial, int length, long arrayClassId,
                           long fileOffset) implements Sub {
    }

    /**
     * PRIMITIVE_ARRAY_DUMP (tag 0x23): a primitive array. {@code elementType}
     * is the HPROF basic-type byte (see {@link HprofTag.BasicType}).
     */
    record PrimitiveArrayDump(long arrayId, int traceSerial, int length, int elementType,
                              long fileOffset) implements Sub {
    }

    /** A sub-record whose tag is recognised but whose body is not yet decoded. */
    record OpaqueSub(int tag, long fileOffset, long byteLength) implements Sub {
        public OpaqueSub {
            if (byteLength < 0) {
                throw new IllegalArgumentException("byteLength must be non-negative: byteLength=" + byteLength);
            }
        }
    }
}
