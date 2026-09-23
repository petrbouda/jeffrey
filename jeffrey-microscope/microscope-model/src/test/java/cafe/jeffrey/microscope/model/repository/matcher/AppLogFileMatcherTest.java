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

package cafe.jeffrey.microscope.model.repository.matcher;

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
