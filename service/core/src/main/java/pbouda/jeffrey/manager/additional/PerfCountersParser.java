/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.manager.additional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class PerfCountersParser {

    private static final Logger LOG = LoggerFactory.getLogger(PerfCountersParser.class);

    // Type codes - based on JNI field descriptors
    private static final char TYPE_BOOLEAN = 'Z';
    private static final char TYPE_CHAR = 'C';
    private static final char TYPE_FLOAT = 'F';
    private static final char TYPE_DOUBLE = 'D';
    private static final char TYPE_BYTE = 'B';
    private static final char TYPE_SHORT = 'S';
    private static final char TYPE_INT = 'I';
    private static final char TYPE_LONG = 'J';
    private static final char TYPE_OBJECT = 'L';
    private static final char TYPE_ARRAY = '[';
    private static final char TYPE_VOID = 'V';

    // Variability attributes
    private static final byte VARIABILITY_INVALID = 0;
    private static final byte VARIABILITY_CONSTANT = 1;
    private static final byte VARIABILITY_MONOTONIC = 2;
    private static final byte VARIABILITY_VARIABLE = 3;

    // Units of measure
    private static final byte UNITS_INVALID = 0;
    private static final byte UNITS_NONE = 1;
    private static final byte UNITS_BYTES = 2;
    private static final byte UNITS_TICKS = 3;
    private static final byte UNITS_EVENTS = 4;
    private static final byte UNITS_STRING = 5;
    private static final byte UNITS_HERTZ = 6;

    // PerfData magic number and format constants
    private static final int PERFDATA_MAGIC = 0xcafec0c0;
    private static final byte BYTE_ORDER_BIG_ENDIAN = 0;
    private static final byte BYTE_ORDER_LITTLE_ENDIAN = 1;

    /**
     * Main method to parse and print PerfData file
     */
    public static Map<String, Object> parse(Path path) {
        try {
            if (!Files.exists(path)) {
                LOG.error("PerfData File not found: {}", path);
                return Map.of();
            }

            return parsePerfData(path, true);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing PerfData file: " + path, e);
        }
    }

    /**
     * Parse the PerfData file and return a map of performance counters
     *
     * @param perfdataPath Path to the PerfData file
     * @param parseTime    Whether to parse tick values to nanoseconds
     * @return Map of performance counter names to values
     * @throws IOException If an I/O error occurs
     */
    private static Map<String, Object> parsePerfData(Path perfdataPath, boolean parseTime) throws IOException {
        try (FileChannel channel = FileChannel.open(perfdataPath, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
            channel.read(buffer);
            buffer.flip();

            // Read perfdata header (big endian)
            buffer.order(ByteOrder.BIG_ENDIAN);
            int magic = buffer.getInt();

            if (magic != PERFDATA_MAGIC) {
                // Try little endian if magic number doesn't match
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.position(0);
                magic = buffer.getInt();

                if (magic != PERFDATA_MAGIC) {
                    throw new IOException("Invalid magic number: 0x" + Integer.toHexString(magic));
                }
            }

            byte byteOrder = buffer.get();
            byte majorVersion = buffer.get();
            byte minorVersion = buffer.get();

            // Only support 2.0 perf data buffers
            if (!(majorVersion == 2 && minorVersion == 0)) {
                throw new IOException("Unsupported version " + majorVersion + "." + minorVersion);
            }

            // Set correct byte order for further reading
            if (byteOrder == BYTE_ORDER_BIG_ENDIAN) {
                buffer.order(ByteOrder.BIG_ENDIAN);
            } else if (byteOrder == BYTE_ORDER_LITTLE_ENDIAN) {
                buffer.order(ByteOrder.LITTLE_ENDIAN);
            } else {
                throw new IOException("Invalid byte order: " + byteOrder);
            }

            // Read buffer prologue
            byte accessible = buffer.get();
            if (accessible != 1) {
                throw new IOException("PerfData not accessible: " + accessible);
            }

            int used = buffer.getInt();
            int overflow = buffer.getInt();
            long modTimestamp = buffer.getLong();
            int entryOffset = buffer.getInt();
            int numEntries = buffer.getInt();

            // Process entries
            Map<String, Object> entryMap = new HashMap<>();
            Map<String, Long> unconvertedTickFields = new HashMap<>();
            long frequency = 0;  // For time conversion

            int currentOffset = entryOffset;
            for (int i = 0; i < numEntries; i++) {
                buffer.position(currentOffset);

                // Read entry header
                int entryLength = buffer.getInt();
                int nameOffset = buffer.getInt();
                int vectorLength = buffer.getInt();
                byte dataType = buffer.get();
                byte flags = buffer.get();
                byte dataUnits = buffer.get();
                byte dataVar = buffer.get();
                int dataOffset = buffer.getInt();

                // Read name (null-terminated)
                buffer.position(currentOffset + nameOffset);
                StringBuilder nameBuilder = new StringBuilder();
                byte c;
                while ((c = buffer.get()) != 0) {
                    nameBuilder.append((char) c);
                }
                String name = nameBuilder.toString();

                // Read value
                buffer.position(currentOffset + dataOffset);

                if (vectorLength == 0) {
                    // Scalar value
                    if (dataType != TYPE_LONG) {
                        throw new IOException("Unexpected monitor type: " + dataType);
                    }

                    long value = buffer.getLong();

                    if (parseTime && dataUnits == UNITS_TICKS) {
                        unconvertedTickFields.put(name, value);
                    }

                    if (name.equals("sun.os.hrt.frequency")) {
                        frequency = value;
                    }

                    entryMap.put(name, value);
                } else {
                    // Vector (string) value
                    if (dataType != TYPE_BYTE || dataUnits != UNITS_STRING ||
                        (dataVar != VARIABILITY_CONSTANT && dataVar != VARIABILITY_VARIABLE)) {
                        throw new IOException("Unexpected vector monitor: DataType:" + dataType +
                                              ", DataUnits:" + dataUnits + ", DataVar:" + dataVar);
                    }

                    byte[] bytes = new byte[vectorLength];
                    buffer.get(bytes);

                    // Find null terminator if present
                    int nullPos = -1;
                    for (int j = 0; j < bytes.length; j++) {
                        if (bytes[j] == 0) {
                            nullPos = j;
                            break;
                        }
                    }

                    String value = (nullPos >= 0) ?
                            new String(bytes, 0, nullPos) :
                            new String(bytes);

                    entryMap.put(name, value);
                }

                currentOffset += entryLength;
            }

            // Convert tick values to nanoseconds if frequency is known
            if (frequency > 0) {
                long nanosPerTick = 1_000_000_000L / frequency;
                for (Map.Entry<String, Long> entry : unconvertedTickFields.entrySet()) {
                    entryMap.put(entry.getKey(), entry.getValue() * nanosPerTick);
                }
            }

            return entryMap;
        }
    }
}
