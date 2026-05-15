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
 * Streams top-level records out of an {@link HprofMappedFile}.
 * <p>
 * Each top-level record is laid out as:
 * <pre>
 *   [u1 tag] [u4 timestampDelta] [u4 bodyLength] [body...]
 * </pre>
 * The 4-byte body length is treated as unsigned: HEAP_DUMP_SEGMENT bodies in
 * particular can approach 4 GB on a single segment.
 * <p>
 * Stateless and thread-safe. Decoded records and parse warnings are pushed to
 * the supplied {@link Listener}; the reader does not buffer.
 */
public final class HprofTopLevelReader {

    public interface Listener {
        void onRecord(HprofRecord.Top record);

        default void onWarning(ParseWarning warning) {
        }
    }

    private static final int RECORD_HEADER_BYTES = 9;

    private HprofTopLevelReader() {
    }

    public static void read(HprofMappedFile file, Listener listener) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }

        int idSize = file.header().idSize();
        long fileSize = file.size();
        long offset = file.header().headerSize();

        while (offset < fileSize) {
            if (offset + RECORD_HEADER_BYTES > fileSize) {
                listener.onWarning(new ParseWarning(
                        offset, null, ParseWarning.Severity.ERROR,
                        "Truncated record header: bytesRemaining=" + (fileSize - offset)));
                return;
            }

            int tag = Byte.toUnsignedInt(file.readByte(offset));
            // 4 bytes timestamp delta at offset+1 (ignored)
            long bodyLength = Integer.toUnsignedLong(file.readInt(offset + 5));
            long bodyOffset = offset + RECORD_HEADER_BYTES;

            if (bodyOffset + bodyLength > fileSize) {
                listener.onWarning(new ParseWarning(
                        offset, tag, ParseWarning.Severity.ERROR,
                        "Record body extends beyond EOF: tag=" + tag
                                + " bodyLength=" + bodyLength
                                + " bytesRemaining=" + (fileSize - bodyOffset)));
                return;
            }

            try {
                dispatch(file, tag, bodyOffset, bodyLength, idSize, listener);
            } catch (RuntimeException e) {
                listener.onWarning(new ParseWarning(
                        offset, tag, ParseWarning.Severity.ERROR,
                        "Failed to decode top-level record: tag=" + tag + " error=" + e.getMessage()));
            }

            offset = bodyOffset + bodyLength;
        }
    }

    private static void dispatch(
            HprofMappedFile file, int tag, long bodyOffset, long bodyLength, int idSize, Listener listener) {
        switch (tag) {
            case HprofTag.Top.STRING -> readString(file, bodyOffset, bodyLength, idSize, listener);
            case HprofTag.Top.LOAD_CLASS -> readLoadClass(file, bodyOffset, bodyLength, idSize, listener);
            case HprofTag.Top.STACK_FRAME -> readStackFrame(file, bodyOffset, bodyLength, idSize, listener);
            case HprofTag.Top.STACK_TRACE -> readStackTrace(file, bodyOffset, bodyLength, idSize, listener);
            case HprofTag.Top.HEAP_DUMP -> listener.onRecord(
                    new HprofRecord.HeapDumpRegion(bodyOffset, bodyLength, false));
            case HprofTag.Top.HEAP_DUMP_SEGMENT -> listener.onRecord(
                    new HprofRecord.HeapDumpRegion(bodyOffset, bodyLength, true));
            default -> listener.onRecord(new HprofRecord.OpaqueTop(tag, bodyOffset, bodyLength));
        }
    }

    private static void readString(
            HprofMappedFile file, long bodyOffset, long bodyLength, int idSize, Listener listener) {
        if (bodyLength < idSize) {
            listener.onWarning(new ParseWarning(
                    bodyOffset, HprofTag.Top.STRING, ParseWarning.Severity.WARN,
                    "STRING body too short for id: bodyLength=" + bodyLength + " idSize=" + idSize));
            return;
        }
        long stringId = file.readId(bodyOffset);
        long utf8Length = bodyLength - idSize;
        if (utf8Length > Integer.MAX_VALUE) {
            listener.onWarning(new ParseWarning(
                    bodyOffset, HprofTag.Top.STRING, ParseWarning.Severity.WARN,
                    "STRING body exceeds 2 GB, skipping: utf8Length=" + utf8Length));
            return;
        }
        byte[] utf8 = file.readBytes(bodyOffset + idSize, (int) utf8Length);
        listener.onRecord(new HprofRecord.HprofString(stringId, utf8, bodyOffset));
    }

    private static void readLoadClass(
            HprofMappedFile file, long bodyOffset, long bodyLength, int idSize, Listener listener) {
        long expected = 4L + idSize + 4L + idSize;
        if (bodyLength < expected) {
            listener.onWarning(new ParseWarning(
                    bodyOffset, HprofTag.Top.LOAD_CLASS, ParseWarning.Severity.WARN,
                    "LOAD_CLASS body too short: bodyLength=" + bodyLength + " expected=" + expected));
            return;
        }
        int classSerial = file.readInt(bodyOffset);
        long classId = file.readId(bodyOffset + 4);
        int traceSerial = file.readInt(bodyOffset + 4 + idSize);
        long nameStringId = file.readId(bodyOffset + 4 + idSize + 4);
        listener.onRecord(new HprofRecord.LoadClass(
                classSerial, classId, traceSerial, nameStringId, bodyOffset));
    }

    /**
     * STACK_FRAME body: 4 × id + u4 classSerial + u4 lineNumber.
     */
    private static void readStackFrame(
            HprofMappedFile file, long bodyOffset, long bodyLength, int idSize, Listener listener) {
        long expected = 4L * idSize + 4L + 4L;
        if (bodyLength < expected) {
            listener.onWarning(new ParseWarning(
                    bodyOffset, HprofTag.Top.STACK_FRAME, ParseWarning.Severity.WARN,
                    "STACK_FRAME body too short: bodyLength=" + bodyLength + " expected=" + expected));
            return;
        }
        long cursor = bodyOffset;
        long stackFrameId = file.readId(cursor);
        cursor += idSize;
        long methodNameStringId = file.readId(cursor);
        cursor += idSize;
        long methodSignatureStringId = file.readId(cursor);
        cursor += idSize;
        long sourceFileNameStringId = file.readId(cursor);
        cursor += idSize;
        int classSerial = file.readInt(cursor);
        cursor += 4;
        int lineNumber = file.readInt(cursor);
        listener.onRecord(new HprofRecord.StackFrame(
                stackFrameId, methodNameStringId, methodSignatureStringId,
                sourceFileNameStringId, classSerial, lineNumber, bodyOffset));
    }

    /**
     * STACK_TRACE body: u4 traceSerial + u4 threadSerial + u4 numberOfFrames + numberOfFrames × id.
     */
    private static void readStackTrace(
            HprofMappedFile file, long bodyOffset, long bodyLength, int idSize, Listener listener) {
        long headerSize = 4L + 4L + 4L;
        if (bodyLength < headerSize) {
            listener.onWarning(new ParseWarning(
                    bodyOffset, HprofTag.Top.STACK_TRACE, ParseWarning.Severity.WARN,
                    "STACK_TRACE body too short: bodyLength=" + bodyLength + " expected>=" + headerSize));
            return;
        }
        int traceSerial = file.readInt(bodyOffset);
        int threadSerial = file.readInt(bodyOffset + 4);
        int numberOfFrames = file.readInt(bodyOffset + 8);
        if (numberOfFrames < 0) {
            listener.onWarning(new ParseWarning(
                    bodyOffset, HprofTag.Top.STACK_TRACE, ParseWarning.Severity.WARN,
                    "STACK_TRACE negative frame count, skipping: numberOfFrames=" + numberOfFrames));
            return;
        }
        long expected = headerSize + (long) numberOfFrames * idSize;
        if (bodyLength < expected) {
            listener.onWarning(new ParseWarning(
                    bodyOffset, HprofTag.Top.STACK_TRACE, ParseWarning.Severity.WARN,
                    "STACK_TRACE body too short for frame ids: bodyLength=" + bodyLength
                            + " expected=" + expected));
            return;
        }
        long[] frameIds = new long[numberOfFrames];
        long cursor = bodyOffset + headerSize;
        for (int i = 0; i < numberOfFrames; i++) {
            frameIds[i] = file.readId(cursor);
            cursor += idSize;
        }
        listener.onRecord(new HprofRecord.StackTrace(traceSerial, threadSerial, frameIds, bodyOffset));
    }
}
