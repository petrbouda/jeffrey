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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.profile.heapdump.parser.JdkFieldNames;

/**
 * Decodes a Java {@code String} object from the heap.
 *
 * Supports both layouts:
 * <ul>
 *   <li>Java 9+: {@code byte[] value} + {@code byte coder} (0=LATIN1, 1=UTF-16)</li>
 *   <li>Java 8: {@code char[] value}</li>
 * </ul>
 *
 * Returns the decoded string and the underlying value-array instance id
 * (callers use the latter for dedup-grouping by backing array).
 */
public final class JavaStringDecoder {

    public record Decoded(String content, long valueArrayId, long valueArrayBytes) {
    }

    private JavaStringDecoder() {
    }

    /** Decodes the String at {@code stringInstanceId}, or empty if it isn't a decodable String. */
    public static Optional<Decoded> decode(HeapView view, long stringInstanceId) throws SQLException {
        return decodeInternal(view, stringInstanceId, Integer.MAX_VALUE);
    }

    /**
     * Decodes only the prefix of the String at {@code stringInstanceId}, sized
     * for a preview of at most {@code maxChars} characters. Reads only a
     * bounded prefix of the backing array via
     * {@link HeapView#readPrimitiveArrayBytes(long, int)} so a 100 MB log
     * String costs ~200 B of transient allocation instead of 100 MB. The
     * returned content may be slightly shorter than {@code maxChars} because
     * the read is aligned to byte (not character) boundaries.
     */
    public static Optional<Decoded> decodePreview(HeapView view, long stringInstanceId, int maxChars)
            throws SQLException {
        if (maxChars <= 0) {
            return Optional.empty();
        }
        return decodeInternal(view, stringInstanceId, maxChars);
    }

    private static Optional<Decoded> decodeInternal(HeapView view, long stringInstanceId, int maxChars)
            throws SQLException {
        InstanceRow inst = view.findInstanceById(stringInstanceId).orElse(null);
        if (inst == null || inst.kind() != InstanceRow.Kind.INSTANCE) {
            return Optional.empty();
        }
        List<InstanceFieldValue> fields = view.readInstanceFields(stringInstanceId);

        Long valueArrayId = null;
        Byte coder = null;
        for (InstanceFieldValue f : fields) {
            switch (f.name()) {
                case JdkFieldNames.STRING_VALUE -> {
                    if (f.value() instanceof Long ref) {
                        valueArrayId = ref;
                    }
                }
                case JdkFieldNames.STRING_CODER -> {
                    if (f.value() instanceof Byte b) {
                        coder = b;
                    }
                }
                default -> {
                    // ignore other fields
                }
            }
        }

        if (valueArrayId == null || valueArrayId == 0L) {
            return Optional.empty();
        }

        InstanceRow array = view.findInstanceById(valueArrayId).orElse(null);
        if (array == null || array.kind() != InstanceRow.Kind.PRIMITIVE_ARRAY
                || array.primitiveType() == null) {
            return Optional.empty();
        }

        int maxBytes = boundedReadSize(maxChars, array.primitiveType(), coder);
        byte[] bytes = maxBytes == Integer.MAX_VALUE
                ? view.readPrimitiveArrayBytes(valueArrayId)
                : view.readPrimitiveArrayBytes(valueArrayId, maxBytes);
        long arrayShallow = array.shallowSize();
        String content = decodeContent(bytes, array.primitiveType(), coder);
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(new Decoded(content, valueArrayId, arrayShallow));
    }

    /**
     * Number of bytes to read for a {@code maxChars}-character preview, given
     * the array's element type and the String's {@code coder}. Returns
     * {@link Integer#MAX_VALUE} when {@code maxChars} itself is unbounded.
     */
    private static int boundedReadSize(int maxChars, int elementType, Byte coder) {
        if (maxChars == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        int bytesPerChar;
        if (elementType == HprofTag.BasicType.BYTE) {
            // Java 9+ compact: LATIN1 = 1 byte/char, UTF-16 = 2 bytes/char.
            bytesPerChar = (coder != null && coder == 1) ? 2 : 1;
        } else if (elementType == HprofTag.BasicType.CHAR) {
            // Java 8 char[]: 2 bytes per UTF-16 code unit.
            bytesPerChar = 2;
        } else {
            return Integer.MAX_VALUE;
        }
        long bytes = (long) maxChars * bytesPerChar;
        return bytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes;
    }

    /**
     * Decodes raw HPROF array payload bytes into a Java String. Public so the
     * {@code HprofIndex} string-content materialisation phase can reuse the
     * same decode logic without going through the per-instance lookup path.
     *
     * @param elementType the HPROF basic-type tag of the backing array
     *                    ({@link HprofTag.BasicType#BYTE} for compact Java 9+
     *                    Strings, {@link HprofTag.BasicType#CHAR} for Java 8)
     * @param coder       the Java 9+ {@code coder} byte (0=LATIN1, 1=UTF-16);
     *                    ignored for CHAR arrays
     * @return decoded String, or {@code null} if {@code elementType} isn't BYTE or CHAR
     */
    public static String decodeContent(byte[] bytes, int elementType, Byte coder) {
        if (elementType == HprofTag.BasicType.BYTE) {
            // Java 9+ compact strings.
            if (coder != null && coder == 1) {
                return new String(bytes, StandardCharsets.UTF_16); // big-endian per HPROF
            }
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
        if (elementType == HprofTag.BasicType.CHAR) {
            // Java 8 char[]: each element is a UTF-16 code unit, big-endian on disk.
            int n = bytes.length / 2;
            char[] chars = new char[n];
            for (int i = 0; i < n; i++) {
                int hi = bytes[i * 2] & 0xFF;
                int lo = bytes[i * 2 + 1] & 0xFF;
                chars[i] = (char) ((hi << 8) | lo);
            }
            return new String(chars);
        }
        return null;
    }
}
