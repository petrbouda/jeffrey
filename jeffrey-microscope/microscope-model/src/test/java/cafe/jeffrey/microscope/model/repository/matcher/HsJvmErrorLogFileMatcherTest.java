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

package cafe.jeffrey.microscope.model.repository.matcher;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HsJvmErrorLogFileMatcherTest {

    private final HsJvmErrorLogFileMatcher matcher = new HsJvmErrorLogFileMatcher();

    @Nested
    class JeffreysOwnName {

        @Test
        void theProvisionersFile() {
            assertTrue(matcher.test("hs-jvm-err.log"));
        }

        @Test
        void onlyTheWholeName() {
            assertFalse(matcher.test("app-hs-jvm-err.log"));
            assertFalse(matcher.test("hs-jvm-err.log.gz"));
            assertFalse(matcher.test("hs-jvm-err.log.1"));
        }
    }

    /**
     * What a JVM Jeffrey did not configure writes: {@code -XX:ErrorFile} defaults to
     * {@code ./hs_err_pid%p.log}, and {@code %t} may add a timestamp after the pid.
     */
    @Nested
    class JvmDefaultName {

        @Test
        void thePidForm() {
            assertTrue(matcher.test("hs_err_pid12345.log"));
        }

        @Test
        void thePidAndTimestampForm() {
            assertTrue(matcher.test("hs_err_pid12345_2026-09-14_10-20-30.log"));
        }

        @Test
        void notWithoutThePid() {
            assertFalse(matcher.test("hs_err.log"));
            assertFalse(matcher.test("hs_err_pid.log"));
        }

        @Test
        void notWithAnythingElseAfterThePid() {
            assertFalse(matcher.test("hs_err_pid12345-copy.log"));
            assertFalse(matcher.test("hs_err_pid12345.log.gz"));
        }
    }

    @Test
    void nullIsNotAFileName() {
        assertFalse(matcher.test(null));
    }
}
