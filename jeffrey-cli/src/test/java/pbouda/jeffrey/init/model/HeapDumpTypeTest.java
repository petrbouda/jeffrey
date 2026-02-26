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

package pbouda.jeffrey.init.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeapDumpTypeTest {

    @Nested
    class Resolve {

        @Test
        void resolvesExitLowercase() {
            assertEquals(HeapDumpType.EXIT, HeapDumpType.resolve("exit"));
        }

        @Test
        void resolvesExitUppercase() {
            assertEquals(HeapDumpType.EXIT, HeapDumpType.resolve("EXIT"));
        }

        @Test
        void resolvesExitMixedCase() {
            assertEquals(HeapDumpType.EXIT, HeapDumpType.resolve("Exit"));
        }

        @Test
        void resolvesCrashLowercase() {
            assertEquals(HeapDumpType.CRASH, HeapDumpType.resolve("crash"));
        }

        @Test
        void resolvesCrashUppercase() {
            assertEquals(HeapDumpType.CRASH, HeapDumpType.resolve("CRASH"));
        }

        @Test
        void resolvesCrashMixedCase() {
            assertEquals(HeapDumpType.CRASH, HeapDumpType.resolve("Crash"));
        }

        @Test
        void throwsExceptionForNullValue() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> HeapDumpType.resolve(null)
            );
            assertEquals("Repository type cannot be null", exception.getMessage());
        }

        @Test
        void throwsExceptionForInvalidValue() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> HeapDumpType.resolve("invalid")
            );
            assertEquals("Invalid heap dump type: invalid", exception.getMessage());
        }

        @Test
        void throwsExceptionForEmptyValue() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> HeapDumpType.resolve("")
            );
            assertEquals("Invalid heap dump type: ", exception.getMessage());
        }
    }
}
