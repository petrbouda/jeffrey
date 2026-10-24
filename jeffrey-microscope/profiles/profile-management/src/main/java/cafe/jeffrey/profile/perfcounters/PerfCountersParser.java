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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads a HotSpot perf-data file ({@code hsperfdata}, version 2.0): every counter by name, a number
 * as a {@link Long} and a string as a {@link String}. Counters measured in ticks of the high-resolution
 * timer are converted to nanoseconds with the file's own {@code sun.os.hrt.frequency}.
 */
public final class PerfCountersParser {

    private static final int PERFDATA_MAGIC = 0xcafec0c0;
    private static final byte BYTE_ORDER_BIG_ENDIAN = 0;
    private static final byte BYTE_ORDER_LITTLE_ENDIAN = 1;
    private static final byte SUPPORTED_MAJOR_VERSION = 2;
    private static final byte SUPPORTED_MINOR_VERSION = 0;
    private static final byte ACCESSIBLE = 1;

    private static final byte TYPE_LONG = 'J';
    private static final byte TYPE_BYTE = 'B';
    private static final byte UNITS_TICKS = 3;
    private static final byte UNITS_STRING = 5;

    private static final String FREQUENCY_COUNTER = "sun.os.hrt.frequency";
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private PerfCountersParser() {
    }

    /**
     * @throws IllegalArgumentException when the file is not a perf-data file this parser reads
     * @throws UncheckedIOException     when the file cannot be read
     */
    public static Map<String, Object> parse(Path path) {
        byte[] content;
        try {
            content = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the perf-data file: " + path, e);
        }

        try {
            return parse(ByteBuffer.wrap(content));
        } catch (BufferUnderflowException | IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Truncated perf-data file: " + path, e);
        }
    }

    private static Map<String, Object> parse(ByteBuffer buffer) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        int magic = buffer.getInt();
        if (magic != PERFDATA_MAGIC) {
            throw new IllegalArgumentException("Not a perf-data file: magic=0x" + Integer.toHexString(magic));
        }

        byte byteOrder = buffer.get();
        byte majorVersion = buffer.get();
        byte minorVersion = buffer.get();
        if (majorVersion != SUPPORTED_MAJOR_VERSION || minorVersion != SUPPORTED_MINOR_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported perf-data version: " + majorVersion + "." + minorVersion);
        }
        buffer.order(switch (byteOrder) {
            case BYTE_ORDER_BIG_ENDIAN -> ByteOrder.BIG_ENDIAN;
            case BYTE_ORDER_LITTLE_ENDIAN -> ByteOrder.LITTLE_ENDIAN;
            default -> throw new IllegalArgumentException("Invalid perf-data byte order: " + byteOrder);
        });

        byte accessible = buffer.get();
        if (accessible != ACCESSIBLE) {
            throw new IllegalArgumentException("Perf-data file is not accessible: " + accessible);
        }

        buffer.getInt();  // used
        buffer.getInt();  // overflow
        buffer.getLong(); // modification time stamp
        int entryOffset = buffer.getInt();
        int entryCount = buffer.getInt();

        Map<String, Object> counters = new HashMap<>();
        Map<String, Long> ticks = new HashMap<>();
        int offset = entryOffset;
        for (int i = 0; i < entryCount; i++) {
            buffer.position(offset);
            int entryLength = buffer.getInt();
            int nameOffset = buffer.getInt();
            int vectorLength = buffer.getInt();
            byte dataType = buffer.get();
            buffer.get();     // flags
            byte units = buffer.get();
            buffer.get();     // variability
            int dataOffset = buffer.getInt();

            String name = readName(buffer, offset + nameOffset);
            buffer.position(offset + dataOffset);
            if (vectorLength == 0) {
                if (dataType != TYPE_LONG) {
                    throw new IllegalArgumentException("Unexpected scalar type: counter=" + name + " type=" + dataType);
                }
                long value = buffer.getLong();
                counters.put(name, value);
                if (units == UNITS_TICKS) {
                    ticks.put(name, value);
                }
            } else if (dataType == TYPE_BYTE && units == UNITS_STRING) {
                counters.put(name, readString(buffer, vectorLength));
            }
            // Byte arrays that are not strings carry nothing a reader of counters can use.

            if (entryLength <= 0) {
                throw new IllegalArgumentException("Invalid perf-data entry length: counter=" + name);
            }
            offset += entryLength;
        }

        if (counters.get(FREQUENCY_COUNTER) instanceof Long frequency && frequency > 0) {
            ticks.forEach((name, value) -> counters.put(name, toNanos(value, frequency)));
        }
        return counters;
    }

    /**
     * Converts per value rather than through a whole number of nanoseconds per tick, which is zero for
     * a timer faster than 1 GHz; split into whole seconds and the remainder so the product cannot
     * overflow.
     */
    static long toNanos(long ticks, long frequency) {
        return ticks / frequency * NANOS_PER_SECOND + ticks % frequency * NANOS_PER_SECOND / frequency;
    }

    private static String readName(ByteBuffer buffer, int position) {
        int end = position;
        while (buffer.get(end) != 0) {
            end++;
        }
        return new String(buffer.array(), position, end - position, StandardCharsets.US_ASCII);
    }

    private static String readString(ByteBuffer buffer, int length) {
        int start = buffer.position();
        int end = start;
        while (end < start + length && buffer.get(end) != 0) {
            end++;
        }
        return new String(buffer.array(), start, end - start, StandardCharsets.UTF_8);
    }
}
