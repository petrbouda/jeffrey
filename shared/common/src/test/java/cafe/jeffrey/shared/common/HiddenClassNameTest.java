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

package cafe.jeffrey.shared.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiddenClassNameTest {

    @Nested
    @DisplayName("Hidden classes")
    class Hidden {

        @Test
        void lambdaProxyWithDotSeparator() {
            HiddenClassName split = HiddenClassName.split(
                    "org.springframework.security.web.FilterChainProxy$$Lambda.0x0000000011cb1be8");

            assertTrue(split.isHidden());
            assertEquals("org.springframework.security.web.FilterChainProxy$$Lambda", split.className());
            assertEquals("0x0000000011cb1be8", split.hiddenClassId());
        }

        @Test
        void lambdaProxyWithSlashSeparator() {
            HiddenClassName split = HiddenClassName.split("com.example.Host$$Lambda/0x00007f8a1c0a1234");

            assertTrue(split.isHidden());
            assertEquals("com.example.Host$$Lambda", split.className());
            assertEquals("0x00007f8a1c0a1234", split.hiddenClassId());
        }

        @Test
        void lambdaProxyWithPlusSeparator() {
            HiddenClassName split = HiddenClassName.split("com.example.Host$$Lambda+0x00007f8a1c0a1234");

            assertTrue(split.isHidden());
            assertEquals("com.example.Host$$Lambda", split.className());
            assertEquals("0x00007f8a1c0a1234", split.hiddenClassId());
        }

        @Test
        void methodHandleLambdaForm() {
            HiddenClassName split = HiddenClassName.split("java.lang.invoke.LambdaForm$MH.0x0000000011dff000");

            assertTrue(split.isHidden());
            assertEquals("java.lang.invoke.LambdaForm$MH", split.className());
            assertEquals("0x0000000011dff000", split.hiddenClassId());
        }

        @Test
        void indifiedStringConcat() {
            HiddenClassName split = HiddenClassName.split("java.lang.String$$StringConcat.0x0000000011049000");

            assertTrue(split.isHidden());
            assertEquals("java.lang.String$$StringConcat", split.className());
            assertEquals("0x0000000011049000", split.hiddenClassId());
        }

        @Test
        void uppercaseHexAddress() {
            HiddenClassName split = HiddenClassName.split("com.example.Host$$Lambda.0x00007F8A1C0A1234");

            assertTrue(split.isHidden());
            assertEquals("0x00007F8A1C0A1234", split.hiddenClassId());
        }

        @Test
        void theSameLambdaFromTwoRunsSharesItsClassName() {
            HiddenClassName runA = HiddenClassName.split(
                    "org.springframework.security.web.FilterChainProxy$$Lambda.0x0000000011cb1be8");
            HiddenClassName runB = HiddenClassName.split(
                    "org.springframework.security.web.FilterChainProxy$$Lambda.0x0000000028cb5fc8");

            assertEquals(runA.className(), runB.className());
        }
    }

    @Nested
    @DisplayName("Ordinary classes")
    class Ordinary {

        @Test
        void plainClassName() {
            HiddenClassName split = HiddenClassName.split("org.springframework.security.web.FilterChainProxy");

            assertFalse(split.isHidden());
            assertEquals("org.springframework.security.web.FilterChainProxy", split.className());
            assertNull(split.hiddenClassId());
        }

        @Test
        void nestedClassName() {
            HiddenClassName split = HiddenClassName.split("java.lang.invoke.StringConcatFactory$InlineHiddenClassStrategy");

            assertFalse(split.isHidden());
            assertEquals("java.lang.invoke.StringConcatFactory$InlineHiddenClassStrategy", split.className());
        }

        @Test
        void jdkProxyIsNotAHiddenClass() {
            HiddenClassName split = HiddenClassName.split("jdk.proxy2.$Proxy47");

            assertFalse(split.isHidden());
            assertEquals("jdk.proxy2.$Proxy47", split.className());
        }

        @Test
        void cglibProxyIsNotAHiddenClass() {
            HiddenClassName split = HiddenClassName.split("com.example.Service$$SpringCGLIB$$0");

            assertFalse(split.isHidden());
            assertEquals("com.example.Service$$SpringCGLIB$$0", split.className());
        }

        @Test
        void nativeFramesCarryAnEmptyClassName() {
            HiddenClassName split = HiddenClassName.split("");

            assertFalse(split.isHidden());
            assertEquals("", split.className());
        }

        @Test
        void nullIsPassedThrough() {
            HiddenClassName split = HiddenClassName.split(null);

            assertFalse(split.isHidden());
            assertNull(split.className());
        }
    }

    @Nested
    @DisplayName("Near misses")
    class NearMisses {

        @Test
        void suffixWithoutHexDigits() {
            HiddenClassName split = HiddenClassName.split("com.example.Host$$Lambda.0x");

            assertFalse(split.isHidden());
            assertEquals("com.example.Host$$Lambda.0x", split.className());
        }

        @Test
        void suffixWithNonHexDigits() {
            HiddenClassName split = HiddenClassName.split("com.example.Host.0xNotHex");

            assertFalse(split.isHidden());
            assertEquals("com.example.Host.0xNotHex", split.className());
        }

        @Test
        void addressWithoutAnythingInFrontOfIt() {
            HiddenClassName split = HiddenClassName.split("0x0000000011cb1be8");

            assertFalse(split.isHidden());
            assertEquals("0x0000000011cb1be8", split.className());
        }

        @Test
        void addressInTheMiddleIsNotASuffix() {
            HiddenClassName split = HiddenClassName.split("com.example.Host.0x1234.Inner");

            assertFalse(split.isHidden());
            assertEquals("com.example.Host.0x1234.Inner", split.className());
        }
    }
}
