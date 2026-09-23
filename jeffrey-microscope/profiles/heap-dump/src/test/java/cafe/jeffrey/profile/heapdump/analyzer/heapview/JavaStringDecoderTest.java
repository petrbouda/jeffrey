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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import cafe.jeffrey.profile.heapdump.view.HprofTag;
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
