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

package pbouda.jeffrey.provider.reader.jfr.chunk;

import jdk.jfr.Event;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.*;

public abstract class EventTypeParser implements JfrChunkConstants {

    private static final int INITIAL_METADATA_BUFFER_SIZE = 64 * 1024; // 64KB initial buffer

    /**
     * Result of streaming event type extraction, containing event types and bytes consumed.
     */
    record EventTypeResult(Set<String> eventTypes, long bytesConsumed) {
    }

    private record Element(String name, Map<String, String> attributes, int childCount) {
    }

    /**
     * Extracts event types from metadata section via streaming.
     * Call after reading the 68-byte chunk header. The input stream should be positioned
     * right after the header.
     *
     * @param input  InputStream positioned after chunk header
     * @param header Chunk header containing offsetMeta and size
     * @return EventTypeResult containing event types and total bytes consumed from stream
     * @throws IOException if reading fails
     */
    static EventTypeResult extractEventTypesStreaming(InputStream input, JfrChunkHeader header) throws IOException {
        long bytesConsumed = 0;

        // Skip from current position (after header) to metadata section
        long bytesToSkip = header.offsetMeta() - CHUNK_HEADER_SIZE;
        if (bytesToSkip > 0) {
            StreamUtils.skipFully(input, bytesToSkip);
            bytesConsumed += bytesToSkip;
        }

        // Read metadata size (first varint in metadata section)
        // We need to read enough bytes to get the size, then read the rest
        byte[] sizeBuffer = new byte[16]; // VarInt is at most 5 bytes, but read more for safety
        int sizeRead = StreamUtils.readFully(input, sizeBuffer, 0, sizeBuffer.length);
        if (sizeRead < 1) {
            throw new EOFException("Cannot read metadata size");
        }

        ByteBuffer sizeBuf = ByteBuffer.wrap(sizeBuffer, 0, sizeRead);
        sizeBuf.order(ByteOrder.BIG_ENDIAN);
        int metaSize = readVarInt(sizeBuf);

        // Calculate remaining bytes to read (we already read sizeRead bytes from metadata)
        int remainingMetaBytes = Math.max(0, metaSize - sizeRead);
        byte[] metaBytes = new byte[sizeRead + remainingMetaBytes + 16]; // Add padding for safety

        // Copy ALL already read bytes (not just the varint)
        System.arraycopy(sizeBuffer, 0, metaBytes, 0, sizeRead);

        // Read remaining bytes
        int toRead = remainingMetaBytes;
        int offset = sizeRead;
        while (toRead > 0) {
            int read = input.read(metaBytes, offset, toRead);
            if (read < 0) {
                break; // EOF - might be at end of chunk
            }
            offset += read;
            toRead -= read;
        }

        bytesConsumed += offset; // Total bytes consumed from metadata section

        // Parse event types from buffer
        ByteBuffer metaBuffer = ByteBuffer.wrap(metaBytes, 0, offset);
        metaBuffer.order(ByteOrder.BIG_ENDIAN);
        Set<String> eventTypes = readEventTypeNames(metaBuffer);

        // Calculate remaining bytes to skip to reach next chunk
        // Total chunk size - header - bytes consumed after header = remaining
        long remaining = header.sizeInBytes() - CHUNK_HEADER_SIZE - bytesConsumed;
        if (remaining > 0) {
            StreamUtils.skipFully(input, remaining);
            bytesConsumed += remaining;
        }

        return new EventTypeResult(eventTypes, bytesConsumed);
    }

    static Set<String> extractEventTypes(FileChannel channel, long chunkPosition, RawChunkHeader header) throws IOException {
        // Allocate a buffer for the metadata section size (initial 32 bytes should be enough)
        ByteBuffer metaSizeBuffer = ByteBuffer.allocate(32);
        metaSizeBuffer.order(ByteOrder.BIG_ENDIAN);
        channel.position(chunkPosition + header.offsetMeta());
        channel.read(metaSizeBuffer);
        metaSizeBuffer.flip();

        // Read the size of the metadata section
        int metaSize = readVarInt(metaSizeBuffer);

        // Now read the entire metadata section
        ByteBuffer metaBuffer = ByteBuffer.allocate(metaSize + 16); // Add padding
        metaBuffer.order(ByteOrder.BIG_ENDIAN);
        channel.position(chunkPosition + header.offsetMeta());
        channel.read(metaBuffer);
        metaBuffer.flip();

        return readEventTypeNames(metaBuffer);
    }

    private static Set<String> readEventTypeNames(ByteBuffer buffer) {
        // Read size and skip initial values
        readVarInt(buffer);
        readVarInt(buffer);     // nTypes
        readVarLong(buffer);    // startTime
        readVarLong(buffer);    // duration
        readVarLong(buffer);    // startTicks

        // Read strings
        int nStrings = readVarInt(buffer);
        String[] strings = new String[nStrings];
        for (int i = 0; i < nStrings; i++) {
            strings[i] = readString(buffer);
        }

        // Read root element and check it's valid
        Element root = readElement(buffer, strings, false);
        if (!root.name.equals("root")) {
            throw new RuntimeException("Expected root element, got " + root.name);
        }

        // Read metadata element
        Set<String> nameToClass = new HashSet<>();
        for (int i = 0; i < root.childCount; i++) {
            Element meta = readElement(buffer, strings, false);

            if (meta.name.equals("metadata")) {
                // Process metadata contents
                for (int j = 0; j < meta.childCount; j++) {
                    Element classElement = readElement(buffer, strings, true);

                    // Process class fields
                    for (int k = 0; k < classElement.childCount; k++) {
                        Element field = readElement(buffer, strings, true);
                        // Skip field processing for now, we just need class names

                        // Skip field children
                        for (int l = 0; l < field.childCount; l++) {
                            readElement(buffer, strings, false);
                        }
                    }

                    // Add class to type map
                    String superType = classElement.attributes.get("superType");
                    if (Event.class.getName().equals(superType)) {
                        String name = classElement.attributes.get("name");
                        nameToClass.add(name);
                    }
                }
            } else if (meta.name.equals("region")) {
                // Skip region
            } else {
                throw new RuntimeException("Unexpected element " + meta.name);
            }
        }

        return nameToClass;
    }

    private static Element readElement(ByteBuffer buffer, String[] strings, boolean needAttributes) {
        int nameIndex = readVarInt(buffer);
        if (nameIndex < 0 || nameIndex >= strings.length) {
            throw new RuntimeException("Invalid string index: " + nameIndex);
        }

        String name = strings[nameIndex];
        int attributeCount = readVarInt(buffer);

        Map<String, String> attributes = null;
        if (needAttributes) {
            attributes = new HashMap<>();
            for (int i = 0; i < attributeCount; i++) {
                int attrNameIndex = readVarInt(buffer);
                int attrValueIndex = readVarInt(buffer);

                if (attrNameIndex < 0 || attrNameIndex >= strings.length ||
                        attrValueIndex < 0 || attrValueIndex >= strings.length) {
                    throw new RuntimeException("Invalid attribute index");
                }

                attributes.put(strings[attrNameIndex], strings[attrValueIndex]);
            }
        } else {
            // Skip attributes
            for (int i = 0; i < attributeCount; i++) {
                readVarInt(buffer);  // Skip name
                readVarInt(buffer);  // Skip value
            }
        }

        int childCount = readVarInt(buffer);
        return new Element(name, attributes, childCount);
    }

    private static int readVarInt(ByteBuffer buffer) {
        int value = 0;
        for (int shift = 0; shift < 32; shift += 7) {
            if (buffer.remaining() == 0) {
                throw new RuntimeException("Unexpected end of buffer");
            }

            byte b = buffer.get();
            value |= (b & 0x7F) << shift;
            if (b >= 0) { // Check sign bit
                break;
            }
        }
        return value;
    }

    private static long readVarLong(ByteBuffer buffer) {
        long value = 0;
        for (int shift = 0; shift <= 56; shift += 7) {
            if (buffer.remaining() == 0) {
                throw new RuntimeException("Unexpected end of buffer");
            }

            byte b = buffer.get();
            if (shift == 56) {
                value |= ((long) b & 0xFF) << shift;
                break;
            } else {
                value |= ((long) b & 0x7F) << shift;
                if (b >= 0) { // Check sign bit
                    break;
                }
            }
        }
        return value;
    }

    private static String readString(ByteBuffer buffer) {
        byte type = buffer.get();
        return switch (type) {
            case 0, 1 ->  "";
            case 3 -> {
                byte[] bytes = readBytes(buffer);
                yield new String(bytes);
            }
            case 4 -> readCharArrayString(buffer);
            default -> throw new RuntimeException("Unknown string type: " + type);
        };
    }

    private static String readCharArrayString(ByteBuffer buffer) {
        int length = readVarInt(buffer);
        if (length < 0) {
            throw new RuntimeException("Invalid string length");
        }

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int c = readVarInt(buffer);
            sb.append((char) c);
        }

        return sb.toString();
    }

    private static byte[] readBytes(ByteBuffer buffer) {
        int length = readVarInt(buffer);
        if (length < 0) {
            throw new RuntimeException("Invalid byte array length");
        }

        if (buffer.remaining() < length) {
            throw new RuntimeException("Unexpected end of buffer");
        }

        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return bytes;
    }
}
