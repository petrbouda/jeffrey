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

import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JavaStringDecoderTest {

    @Nested
    class CompactByteArrays {

        @Test
        void coderZeroDecodesAsLatin1() {
            byte[] bytes = "café".getBytes(StandardCharsets.ISO_8859_1);
            String decoded = JavaStringDecoder.decodeContent(bytes, HprofTag.BasicType.BYTE, (byte) 0);
            assertEquals("café", decoded);
        }

        @Test
        void coderOneDecodesAsUtf16BigEndian() {
            byte[] bytes = "héllo".getBytes(StandardCharsets.UTF_16BE);
            // String(bytes, UTF_16) interprets as BE in absence of a BOM; HPROF stores
            // UTF-16 bytes in big-endian order, matching the JDK's compact-strings layout.
            String decoded = JavaStringDecoder.decodeContent(bytes, HprofTag.BasicType.BYTE, (byte) 1);
            assertEquals("héllo", decoded);
        }

        @Test
        void nullCoderFallsBackToLatin1() {
            // Defensive default: when no `coder` field is observable on the String
            // instance, decodeContent treats the byte[] as LATIN1. Confirmed by
            // the production check `coder != null && coder == 1` in JavaStringDecoder.
            byte[] bytes = "abc".getBytes(StandardCharsets.ISO_8859_1);
            String decoded = JavaStringDecoder.decodeContent(bytes, HprofTag.BasicType.BYTE, null);
            assertEquals("abc", decoded);
        }

        @Test
        void emptyBytesDecodeToEmptyString() {
            String decoded = JavaStringDecoder.decodeContent(new byte[0], HprofTag.BasicType.BYTE, (byte) 0);
            assertEquals("", decoded);
        }
    }

    @Nested
    class LegacyCharArrays {

        @Test
        void java8CharArrayDecodesAsBigEndianUtf16() {
            // Java 8 String.value is char[]. HPROF serialises each char as two
            // big-endian bytes: hi byte then lo byte.
            byte[] bytes = {
                    0x00, 0x48, // 'H'
                    0x00, 0x65, // 'e'
                    0x00, 0x6C, // 'l'
                    0x00, 0x6C, // 'l'
                    0x00, 0x6F  // 'o'
            };
            String decoded = JavaStringDecoder.decodeContent(bytes, HprofTag.BasicType.CHAR, null);
            assertEquals("Hello", decoded);
        }

        @Test
        void coderArgumentIgnoredForCharArrays() {
            byte[] bytes = {0x00, 0x41}; // 'A'
            // Pass a non-null coder to confirm CHAR path ignores it entirely.
            String decoded = JavaStringDecoder.decodeContent(bytes, HprofTag.BasicType.CHAR, (byte) 1);
            assertEquals("A", decoded);
        }
    }

    @Nested
    class UnsupportedElementType {

        @Test
        void intElementTypeReturnsNull() {
            // String's backing array must be BYTE (Java 9+) or CHAR (Java 8);
            // anything else means we're not looking at a real java.lang.String.
            assertNull(JavaStringDecoder.decodeContent(new byte[]{1, 2, 3, 4}, HprofTag.BasicType.INT, (byte) 0));
        }
    }
}
