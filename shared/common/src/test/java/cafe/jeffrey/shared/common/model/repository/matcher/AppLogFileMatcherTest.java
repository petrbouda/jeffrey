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

package cafe.jeffrey.shared.common.model.repository.matcher;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppLogFileMatcherTest {

    private final AppLogFileMatcher matcher = new AppLogFileMatcher();

    @Nested
    class PlainAndRotatedAfterExtension {

        @Test
        void thePlainFile() {
            assertTrue(matcher.test("service.log"));
            assertTrue(matcher.test("service-app.log"));
        }

        @Test
        void anIndexAfterTheExtension() {
            assertTrue(matcher.test("service.log.1"));
        }

        @Test
        void aDateAfterTheExtension() {
            assertTrue(matcher.test("service.log.2026-09-13"));
            assertTrue(matcher.test("service.log.2026-09-13.1"));
        }
    }

    /**
     * Logback's {@code %d} and {@code %i} and Log4j2's {@code filePattern} usually sit before
     * {@code .log}.
     */
    @Nested
    class RotatedBeforeExtension {

        @Test
        void aDateBeforeTheExtension() {
            assertTrue(matcher.test("service.2026-09-13.log"));
        }

        @Test
        void aDateAndIndexBeforeTheExtension() {
            assertTrue(matcher.test("service-2026-09-13.1.log"));
        }
    }

    @Nested
    class Compressed {

        @Test
        void gzip() {
            assertTrue(matcher.test("service.log.gz"));
            assertTrue(matcher.test("service.log.1.gz"));
            assertTrue(matcher.test("service.2026-09-13.log.gz"));
        }

        @Test
        void zip() {
            assertTrue(matcher.test("service-2026-09-13.1.log.zip"));
        }

        @Test
        void zstd() {
            assertTrue(matcher.test("service.log.zst"));
        }

        @Test
        void xz() {
            assertTrue(matcher.test("service.log.xz"));
        }

        @Test
        void bzip2() {
            assertTrue(matcher.test("service.log.bz2"));
        }

        @Test
        void lz4() {
            assertTrue(matcher.test("service.log.lz4"));
        }
    }

    @Nested
    class Rejected {

        /** The JVM's own log has an extension of its own precisely so this matcher never sees it. */
        @Test
        void aJvmLog() {
            assertFalse(matcher.test("gc.jvm-log"));
            assertFalse(matcher.test("gc.jvm-log.0"));
        }

        @Test
        void aTailThatIsNeitherRotationNorCompression() {
            assertFalse(matcher.test("service.log.tar"));
            assertFalse(matcher.test("service.log.tar.gz"));
            assertFalse(matcher.test("service.log.old"));
        }

        @Test
        void anotherExtension() {
            assertFalse(matcher.test("service.txt"));
            assertFalse(matcher.test("service.log.gz.txt"));
            assertFalse(matcher.test("servicelog"));
        }

        @Test
        void aMissingName() {
            assertFalse(matcher.test(null));
        }
    }
}
