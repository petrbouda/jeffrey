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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.heapdump.view.HprofTag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a minimal-but-valid HPROF 1.0.2 dump for orchestration tests: one
 * class ("Holder") with a single object field, one sticky-class-rooted
 * instance of it. Mirrors the byte layout of the {@code SyntheticHprof}
 * builder in the heap-dump module's tests, which cannot be shared here — the
 * orchestration module is compiled on the module path and a test-jar would
 * split the parser package of the named heap-dump module.
 */
final class SyntheticHeapDumps {

    private static final String MAGIC_1_0_2 = "JAVA PROFILE 1.0.2";

    private static final int ID_SIZE = 8;

    private static final long HOLDER_CLASS_NAME_ID = 0xA001L;

    private static final long FIELD_NAME_ID = 0xA002L;

    private static final long HOLDER_CLASS_ID = 0xC001L;

    private static final long INSTANCE_ID = 0x100L;

    private SyntheticHeapDumps() {
    }

    /** Writes the minimal dump to {@code dir/fileName} and returns its path. */
    static Path writeMinimalDump(Path dir, String fileName) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);

        out.writeBytes(MAGIC_1_0_2);
        out.writeByte(0);
        out.writeInt(ID_SIZE);
        out.writeLong(0L);

        writeRecord(out, HprofTag.Top.STRING, stringBody(HOLDER_CLASS_NAME_ID, "Holder"));
        writeRecord(out, HprofTag.Top.STRING, stringBody(FIELD_NAME_ID, "next"));
        writeRecord(out, HprofTag.Top.LOAD_CLASS, loadClassBody());
        writeRecord(out, HprofTag.Top.HEAP_DUMP_SEGMENT, heapSegmentBody());
        writeRecord(out, HprofTag.Top.HEAP_DUMP_END, new byte[0]);

        Path path = dir.resolve(fileName);
        Files.write(path, sink.toByteArray());
        return path;
    }

    private static void writeRecord(DataOutputStream out, int tag, byte[] body) throws IOException {
        out.writeByte(tag);
        out.writeInt(0); // timestamp delta
        out.writeInt(body.length);
        out.write(body);
    }

    private static byte[] stringBody(long stringId, String value) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeLong(stringId);
        out.write(value.getBytes(StandardCharsets.UTF_8));
        return sink.toByteArray();
    }

    private static byte[] loadClassBody() throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeInt(1); // class serial
        out.writeLong(HOLDER_CLASS_ID);
        out.writeInt(0); // stack trace serial
        out.writeLong(HOLDER_CLASS_NAME_ID);
        return sink.toByteArray();
    }

    private static byte[] heapSegmentBody() throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);

        // CLASS_DUMP: Holder, no super, one OBJECT-typed instance field.
        out.writeByte(HprofTag.Sub.CLASS_DUMP);
        out.writeLong(HOLDER_CLASS_ID);
        out.writeInt(0);      // stack trace serial
        out.writeLong(0L);    // super class
        out.writeLong(0L);    // classloader
        out.writeLong(0L);    // signers
        out.writeLong(0L);    // protection domain
        out.writeLong(0L);    // reserved 1
        out.writeLong(0L);    // reserved 2
        out.writeInt(ID_SIZE); // instance size = one object reference
        out.writeShort(0);    // const pool count
        out.writeShort(0);    // static fields count
        out.writeShort(1);    // instance fields count
        out.writeLong(FIELD_NAME_ID);
        out.writeByte(HprofTag.BasicType.OBJECT);

        // GC root + one Holder instance with a null field value.
        out.writeByte(HprofTag.Sub.ROOT_STICKY_CLASS);
        out.writeLong(INSTANCE_ID);

        out.writeByte(HprofTag.Sub.INSTANCE_DUMP);
        out.writeLong(INSTANCE_ID);
        out.writeInt(0); // stack trace serial
        out.writeLong(HOLDER_CLASS_ID);
        out.writeInt(ID_SIZE);
        out.writeLong(0L); // field value: null

        return sink.toByteArray();
    }
}
