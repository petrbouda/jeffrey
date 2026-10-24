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

package cafe.jeffrey.profile.perfcounters;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a perf-data file the way HotSpot lays one out (version 2.0, little-endian entries), so the
 * parser can be held to the format without a JVM's own file.
 */
final class PerfDataFixture {

    private static final int MAGIC = 0xcafec0c0;
    private static final int PROLOGUE_SIZE = 32;
    private static final int ENTRY_HEADER_SIZE = 20;
    private static final byte LITTLE_ENDIAN = 1;
    private static final byte TYPE_LONG = 'J';
    private static final byte TYPE_BYTE = 'B';
    private static final byte UNITS_NONE = 1;
    private static final byte UNITS_TICKS = 3;
    private static final byte UNITS_STRING = 5;
    private static final byte UNITS_HERTZ = 6;
    private static final byte VARIABILITY_CONSTANT = 1;

    private record Entry(String name, byte type, byte units, byte[] data, int vectorLength) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private byte major = 2;

    PerfDataFixture number(String name, long value) {
        return add(name, UNITS_NONE, value);
    }

    PerfDataFixture ticks(String name, long value) {
        return add(name, UNITS_TICKS, value);
    }

    PerfDataFixture frequency(long hertz) {
        return add("sun.os.hrt.frequency", UNITS_HERTZ, hertz);
    }

    PerfDataFixture text(String name, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, data, 0, bytes.length);
        entries.add(new Entry(name, TYPE_BYTE, UNITS_STRING, data, data.length));
        return this;
    }

    PerfDataFixture version(int majorVersion) {
        this.major = (byte) majorVersion;
        return this;
    }

    Path writeTo(Path file) {
        try {
            Files.write(file, bytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private PerfDataFixture add(String name, byte units, long value) {
        byte[] data = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array();
        entries.add(new Entry(name, TYPE_LONG, units, data, 0));
        return this;
    }

    private byte[] bytes() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Entry entry : entries) {
            byte[] name = (entry.name() + "\0").getBytes(StandardCharsets.US_ASCII);
            int dataOffset = align(ENTRY_HEADER_SIZE + name.length);
            int length = align(dataOffset + entry.data().length);
            ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(length)
                    .putInt(ENTRY_HEADER_SIZE)
                    .putInt(entry.vectorLength())
                    .put(entry.type())
                    .put((byte) 0)
                    .put(entry.units())
                    .put(VARIABILITY_CONSTANT)
                    .putInt(dataOffset)
                    .put(name);
            buffer.position(dataOffset);
            buffer.put(entry.data());
            body.writeBytes(buffer.array());
        }

        ByteBuffer prologue = ByteBuffer.allocate(PROLOGUE_SIZE);
        prologue.order(ByteOrder.BIG_ENDIAN).putInt(MAGIC);
        prologue.put(LITTLE_ENDIAN).put(major).put((byte) 0).put((byte) 1);
        prologue.order(ByteOrder.LITTLE_ENDIAN)
                .putInt(PROLOGUE_SIZE + body.size())
                .putInt(0)
                .putLong(0L)
                .putInt(PROLOGUE_SIZE)
                .putInt(entries.size());

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(prologue.array());
        file.writeBytes(body.toByteArray());
        return file.toByteArray();
    }

    private static int align(int size) {
        return (size + Long.BYTES - 1) / Long.BYTES * Long.BYTES;
    }
}
