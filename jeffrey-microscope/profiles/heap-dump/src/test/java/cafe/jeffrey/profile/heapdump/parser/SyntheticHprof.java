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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Test-only fluent builder for synthesising HPROF binary streams.
 *
 * Used by parser tests to construct minimal-but-valid dumps without depending
 * on a real captured .hprof file. All numerics are written big-endian as the
 * HPROF format requires.
 */
public final class SyntheticHprof {

    private final ByteArrayOutputStream sink = new ByteArrayOutputStream();
    private final DataOutputStream out = new DataOutputStream(sink);
    private final int idSize;

    private SyntheticHprof(int idSize) {
        this.idSize = idSize;
    }

    public static SyntheticHprof create(String version, int idSize, long timestampMs) {
        try {
            SyntheticHprof h = new SyntheticHprof(idSize);
            h.out.writeBytes(magicFor(version));
            h.out.writeByte(0);
            h.out.writeInt(idSize);
            h.out.writeLong(timestampMs);
            return h;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int idSize() {
        return idSize;
    }

    public SyntheticHprof topLevel(int tag, byte[] body) {
        try {
            out.writeByte(tag);
            out.writeInt(0); // timestamp delta
            out.writeInt(body.length);
            out.write(body);
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public SyntheticHprof string(long stringId, String value) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(body);
        try {
            writeId(d, stringId);
            d.write(value.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return topLevel(HprofTag.Top.STRING, body.toByteArray());
    }

    public SyntheticHprof loadClass(int classSerial, long classId, int traceSerial, long nameStringId) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(body);
        try {
            d.writeInt(classSerial);
            writeId(d, classId);
            d.writeInt(traceSerial);
            writeId(d, nameStringId);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return topLevel(HprofTag.Top.LOAD_CLASS, body.toByteArray());
    }

    public SyntheticHprof heapDumpSegment(Consumer<SubBuilder> body) {
        SubBuilder b = new SubBuilder(idSize);
        body.accept(b);
        return topLevel(HprofTag.Top.HEAP_DUMP_SEGMENT, b.toByteArray());
    }

    public SyntheticHprof heapDumpEnd() {
        return topLevel(HprofTag.Top.HEAP_DUMP_END, new byte[0]);
    }

    /** Append an arbitrary record header with no body bytes (used to test unknown tags). */
    public SyntheticHprof unknownTopLevel(int tag) {
        return topLevel(tag, new byte[0]);
    }

    /** Append raw bytes that DO NOT form a valid record — used to test recovery. */
    public SyntheticHprof appendRaw(byte[] raw) {
        try {
            out.write(raw);
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] toByteArray() {
        return sink.toByteArray();
    }

    public Path writeTo(Path dir, String fileName) throws IOException {
        Path p = dir.resolve(fileName);
        Files.write(p, sink.toByteArray());
        return p;
    }

    private void writeId(DataOutputStream d, long id) throws IOException {
        if (idSize == 4) {
            d.writeInt((int) id);
        } else {
            d.writeLong(id);
        }
    }

    private static String magicFor(String version) {
        return switch (version) {
            case "1.0.1" -> HprofHeader.MAGIC_1_0_1;
            case "1.0.2" -> HprofHeader.MAGIC_1_0_2;
            case "1.0.3" -> HprofHeader.MAGIC_1_0_3;
            default -> throw new IllegalArgumentException("Unsupported version: " + version);
        };
    }

    /** Builder for sub-records inside a HEAP_DUMP / HEAP_DUMP_SEGMENT body. */
    public static final class SubBuilder {
        private final ByteArrayOutputStream sink = new ByteArrayOutputStream();
        private final DataOutputStream out = new DataOutputStream(sink);
        private final int idSize;

        SubBuilder(int idSize) {
            this.idSize = idSize;
        }

        public SubBuilder gcRoot(int rootKind, long instanceId) {
            try {
                out.writeByte(rootKind);
                writeId(instanceId);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        public SubBuilder gcRootJavaFrame(long instanceId, int threadSerial, int frameIndex) {
            try {
                out.writeByte(HprofTag.Sub.ROOT_JAVA_FRAME);
                writeId(instanceId);
                out.writeInt(threadSerial);
                out.writeInt(frameIndex);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        public SubBuilder gcRootThreadObject(long threadObjId, int threadSerial, int stackTraceSerial) {
            try {
                out.writeByte(HprofTag.Sub.ROOT_THREAD_OBJECT);
                writeId(threadObjId);
                out.writeInt(threadSerial);
                out.writeInt(stackTraceSerial);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        /**
         * Minimal CLASS_DUMP: no const pool, no static fields, one int instance field.
         */
        public SubBuilder simpleClassDump(long classId, long superClassId, long classloaderId,
                                   int instanceSize, long instanceFieldNameId) {
            try {
                out.writeByte(HprofTag.Sub.CLASS_DUMP);
                writeId(classId);
                out.writeInt(0); // stack trace serial
                writeId(superClassId);
                writeId(classloaderId);
                writeId(0L); // signers
                writeId(0L); // protection domain
                writeId(0L); // reserved 1
                writeId(0L); // reserved 2
                out.writeInt(instanceSize);
                out.writeShort(0); // const pool count
                out.writeShort(0); // static fields count
                out.writeShort(1); // instance fields count
                writeId(instanceFieldNameId);
                out.writeByte(HprofTag.BasicType.INT);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        public SubBuilder instanceDump(long instanceId, long classId, byte[] fieldBytes) {
            try {
                out.writeByte(HprofTag.Sub.INSTANCE_DUMP);
                writeId(instanceId);
                out.writeInt(0);          // stack trace serial
                writeId(classId);
                out.writeInt(fieldBytes.length);
                out.write(fieldBytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        public SubBuilder objectArrayDump(long arrayId, long arrayClassId, long[] elements) {
            try {
                out.writeByte(HprofTag.Sub.OBJECT_ARRAY_DUMP);
                writeId(arrayId);
                out.writeInt(0);
                out.writeInt(elements.length);
                writeId(arrayClassId);
                for (long e : elements) {
                    writeId(e);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        public SubBuilder primitiveArrayDump(long arrayId, int elementType, byte[] payload, int elementCount) {
            try {
                out.writeByte(HprofTag.Sub.PRIMITIVE_ARRAY_DUMP);
                writeId(arrayId);
                out.writeInt(0);
                out.writeInt(elementCount);
                out.writeByte(elementType);
                out.write(payload);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        public SubBuilder unknownSub(int tag) {
            try {
                out.writeByte(tag);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return this;
        }

        public byte[] toByteArray() {
            return sink.toByteArray();
        }

        private void writeId(long id) throws IOException {
            if (idSize == 4) {
                out.writeInt((int) id);
            } else {
                out.writeLong(id);
            }
        }
    }
}
