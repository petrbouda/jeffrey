/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.provisioner.model;

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
            assertEquals("Heap dump type cannot be null", exception.getMessage());
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
