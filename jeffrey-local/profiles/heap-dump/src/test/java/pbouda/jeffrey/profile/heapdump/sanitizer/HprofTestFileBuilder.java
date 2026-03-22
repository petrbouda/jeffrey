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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fluent builder for constructing HPROF binary test data programmatically.
 * Supports building both valid and corrupted HPROF files for testing the sanitizer.
 */
public class HprofTestFileBuilder {

    private static final String DEFAULT_VERSION = "JAVA PROFILE 1.0.2";
    private static final int DEFAULT_ID_SIZE = 8;

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private String version = DEFAULT_VERSION;
    private int idSize = DEFAULT_ID_SIZE;
    private boolean headerWritten = false;

    public HprofTestFileBuilder version(String version) {
        this.version = version;
        return this;
    }

    public HprofTestFileBuilder idSize(int idSize) {
        this.idSize = idSize;
        return this;
    }

    /**
     * Writes the HPROF header. Must be called before adding records.
     */
    public HprofTestFileBuilder writeHeader() throws IOException {
        // Version string + null terminator
        output.write(version.getBytes(StandardCharsets.US_ASCII));
        output.write(0);

        // ID size (u4)
        writeU4(idSize);

        // Timestamp (u4 high + u4 low)
        writeU4(0);
        writeU4(0);

        headerWritten = true;
        return this;
    }

    /**
     * Adds a UTF8 string record.
     */
    public HprofTestFileBuilder addUtf8Record(long id, String text) throws IOException {
        ensureHeader();
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        int bodyLength = idSize + textBytes.length;

        writeRecordHeader(HprofRecordTag.UTF8.value(), bodyLength);
        writeId(id);
        output.write(textBytes);
        return this;
    }

    /**
     * Adds a LOAD_CLASS record.
     */
    public HprofTestFileBuilder addLoadClassRecord(int serialNo, long classObjId, int stackTraceSerial, long classNameId) throws IOException {
        ensureHeader();
        int bodyLength = 4 + idSize + 4 + idSize;
        writeRecordHeader(HprofRecordTag.LOAD_CLASS.value(), bodyLength);
        writeU4(serialNo);
        writeId(classObjId);
        writeU4(stackTraceSerial);
        writeId(classNameId);
        return this;
    }

    /**
     * Adds a STACK_TRACE record.
     */
    public HprofTestFileBuilder addStackTraceRecord(int serialNo, int threadSerial, int numFrames) throws IOException {
        ensureHeader();
        int bodyLength = 4 + 4 + 4; // serial + thread serial + num frames (no frame IDs)
        writeRecordHeader(HprofRecordTag.TRACE.value(), bodyLength);
        writeU4(serialNo);
        writeU4(threadSerial);
        writeU4(numFrames);
        return this;
    }

    /**
     * Adds a HEAP_DUMP_SEGMENT with the given sub-record data.
     */
    public HprofTestFileBuilder addHeapDumpSegment(byte[] subRecordData) throws IOException {
        ensureHeader();
        writeRecordHeader(HprofRecordTag.HEAP_DUMP_SEGMENT.value(), subRecordData.length);
        output.write(subRecordData);
        return this;
    }

    /**
     * Adds a HEAP_DUMP_SEGMENT with a zero-length body header, but the actual
     * sub-record data follows immediately (simulating the zero-length corruption).
     */
    public HprofTestFileBuilder addZeroLengthHeapDumpSegment(byte[] subRecordData) throws IOException {
        ensureHeader();
        writeRecordHeader(HprofRecordTag.HEAP_DUMP_SEGMENT.value(), 0);
        output.write(subRecordData);
        return this;
    }

    /**
     * Adds a HEAP_DUMP_SEGMENT with a body length that exceeds the actual data written.
     */
    public HprofTestFileBuilder addOverflowedHeapDumpSegment(byte[] subRecordData, int declaredLength) throws IOException {
        ensureHeader();
        writeRecordHeader(HprofRecordTag.HEAP_DUMP_SEGMENT.value(), declaredLength);
        output.write(subRecordData);
        return this;
    }

    /**
     * Adds a HEAP_DUMP_END record.
     */
    public HprofTestFileBuilder addHeapDumpEnd() throws IOException {
        ensureHeader();
        writeRecordHeader(HprofRecordTag.HEAP_DUMP_END.value(), 0);
        return this;
    }

    /**
     * Writes raw bytes directly (for corruption injection).
     */
    public HprofTestFileBuilder writeRawBytes(byte[] data) throws IOException {
        output.write(data);
        return this;
    }

    /**
     * Builds a ROOT_UNKNOWN sub-record.
     */
    public byte[] buildRootUnknownSubRecord(long objectId) {
        ByteBuffer buf = ByteBuffer.allocate(1 + idSize);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) HprofSubRecordTag.ROOT_UNKNOWN.value());
        putId(buf, objectId);
        return buf.array();
    }

    /**
     * Builds a ROOT_STICKY_CLASS sub-record.
     */
    public byte[] buildRootStickyClassSubRecord(long objectId) {
        ByteBuffer buf = ByteBuffer.allocate(1 + idSize);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) HprofSubRecordTag.ROOT_STICKY_CLASS.value());
        putId(buf, objectId);
        return buf.array();
    }

    /**
     * Builds an INSTANCE_DUMP sub-record.
     */
    public byte[] buildInstanceDumpSubRecord(long objectId, int stackTrace, long classId, byte[] instanceData) {
        // tag(1) + id + u4 + id + u4 + data
        int size = 1 + idSize + 4 + idSize + 4 + instanceData.length;
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) HprofSubRecordTag.INSTANCE_DUMP.value());
        putId(buf, objectId);
        buf.putInt(stackTrace);
        putId(buf, classId);
        buf.putInt(instanceData.length);
        buf.put(instanceData);
        return buf.array();
    }

    /**
     * Builds a PRIM_ARRAY_DUMP sub-record for byte arrays.
     */
    public byte[] buildPrimArraySubRecord(long arrayId, int stackTrace, int elementType, byte[] elements) {
        int elementSize = HprofTypeSize.sizeOf(elementType, idSize);
        int numElements = elements.length / elementSize;
        // tag(1) + id + u4 + u4 + u1 + data
        int size = 1 + idSize + 4 + 4 + 1 + elements.length;
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) HprofSubRecordTag.PRIM_ARRAY_DUMP.value());
        putId(buf, arrayId);
        buf.putInt(stackTrace);
        buf.putInt(numElements);
        buf.put((byte) elementType);
        buf.put(elements);
        return buf.array();
    }

    /**
     * Concatenates multiple sub-record byte arrays.
     */
    public byte[] concat(byte[]... arrays) {
        int totalLen = 0;
        for (byte[] a : arrays) totalLen += a.length;
        byte[] result = new byte[totalLen];
        int offset = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, offset, a.length);
            offset += a.length;
        }
        return result;
    }

    /**
     * Returns the built HPROF data as a byte array.
     */
    public byte[] build() {
        return output.toByteArray();
    }

    /**
     * Writes the built HPROF data to a file and returns the path.
     */
    public Path writeTo(Path path) throws IOException {
        Files.write(path, build());
        return path;
    }

    public int getIdSize() {
        return idSize;
    }

    private void ensureHeader() throws IOException {
        if (!headerWritten) {
            writeHeader();
        }
    }

    private void writeRecordHeader(int tag, int bodyLength) throws IOException {
        output.write(tag);
        writeU4(0); // timestamp delta
        writeU4(bodyLength);
    }

    private void writeU4(int value) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(value);
        output.write(buf.array());
    }

    private void writeId(long id) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(idSize);
        buf.order(ByteOrder.BIG_ENDIAN);
        if (idSize == 8) {
            buf.putLong(id);
        } else {
            buf.putInt((int) id);
        }
        output.write(buf.array());
    }

    private void putId(ByteBuffer buf, long id) {
        if (idSize == 8) {
            buf.putLong(id);
        } else {
            buf.putInt((int) id);
        }
    }
}
