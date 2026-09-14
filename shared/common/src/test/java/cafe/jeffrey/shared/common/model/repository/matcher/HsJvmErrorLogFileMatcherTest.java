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

package cafe.jeffrey.shared.common.model.repository.matcher;

import cafe.jeffrey.shared.common.model.repository.SupportedFile;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /**
     * The point of the second spelling: both end in {@code .log}, so without it the JVM's own crash
     * file was filed as an application log and the hub's crash detection never saw it.
     */
    @Nested
    class ThroughSupportedFile {

        @Test
        void theJvmDefaultIsACrashLogNotAnAppLog() {
            assertEquals(SupportedFile.HS_JVM_ERROR_LOG, SupportedFile.of("hs_err_pid123.log"));
        }

        @Test
        void caseIsIgnoredLikeEverywhereElse() {
            assertEquals(SupportedFile.HS_JVM_ERROR_LOG, SupportedFile.of("HS_ERR_PID123.LOG"));
        }
    }
}
