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

package cafe.jeffrey.shared.common.model.repository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportedRecordingFileTest {

    @Nested
    class Recognising {

        @Test
        void recordingsAndTheirCompressedForms() {
            assertEquals(SupportedRecordingFile.JFR, SupportedRecordingFile.of("run.jfr"));
            assertEquals(SupportedRecordingFile.JFR_LZ4, SupportedRecordingFile.of("run.jfr.lz4"));
            assertEquals(SupportedRecordingFile.HEAP_DUMP, SupportedRecordingFile.of("heap.hprof"));
            assertEquals(SupportedRecordingFile.HEAP_DUMP_GZ, SupportedRecordingFile.of("heap.hprof.gz"));
            assertEquals(SupportedRecordingFile.PPROF, SupportedRecordingFile.of("cpu.pprof"));
            assertEquals(SupportedRecordingFile.PPROF, SupportedRecordingFile.of("cpu.pb.gz"));
            assertEquals(SupportedRecordingFile.OTLP_PROFILE, SupportedRecordingFile.of("profiles.otlp"));
        }

        @Test
        void applicationLogsInTheirRotatedAndCompressedForms() {
            assertEquals(SupportedRecordingFile.APP_LOG, SupportedRecordingFile.of("service.log"));
            assertEquals(SupportedRecordingFile.APP_LOG, SupportedRecordingFile.of("service.log.1.gz"));
            assertEquals(SupportedRecordingFile.APP_LOG, SupportedRecordingFile.of("service.2026-09-13.log.gz"));
            assertEquals(SupportedRecordingFile.APP_LOG, SupportedRecordingFile.of("service-2026-09-13.1.log.zip"));
        }

        @Test
        void jvmLogsAndTheirRotatedForms() {
            assertEquals(SupportedRecordingFile.JVM_LOG, SupportedRecordingFile.of("gc.jvm-log"));
            assertEquals(SupportedRecordingFile.JVM_LOG, SupportedRecordingFile.of("gc.jvm-log.0"));
        }

        /**
         * The crash log ends in {@code .log} like an application log; it is the one name that relies on
         * being declared before {@code APP_LOG}, so the order is pinned here rather than left to reordering.
         */
        @Test
        void theCrashLogIsNotAnApplicationLog() {
            assertEquals(SupportedRecordingFile.HS_JVM_ERROR_LOG, SupportedRecordingFile.of("hs-jvm-err.log"));
        }

        /**
         * A compressed recording must not be read as an uncompressed one: the two are stored and
         * decoded differently, and {@code run.jfr.lz4} ends with neither {@code .jfr} nor anything
         * {@code JFR} would claim.
         */
        @Test
        void aCompressedRecordingIsNotThePlainOne() {
            assertFalse(SupportedRecordingFile.JFR.matches("run.jfr.lz4"));
            assertFalse(SupportedRecordingFile.HEAP_DUMP.matches("heap.hprof.gz"));
        }

        @Test
        void anythingElseIsUnknown() {
            assertEquals(SupportedRecordingFile.UNKNOWN, SupportedRecordingFile.of("notes.txt"));
            assertEquals(SupportedRecordingFile.UNKNOWN, SupportedRecordingFile.of("Main.java"));
            assertEquals(SupportedRecordingFile.UNKNOWN, SupportedRecordingFile.of("jfr"));
        }

        @Test
        void aPathIsJudgedByItsFileName() {
            assertEquals(
                    SupportedRecordingFile.JFR,
                    SupportedRecordingFile.of(Path.of("/var/recordings/run.jfr")));
        }
    }

    /**
     * The case of a name on disk says nothing about the format. A dump saved as {@code HEAP.HPROF},
     * or a recording copied through a system that upper-cases names, is the same file — and refusing
     * it as an unsupported type explains nothing to the person holding it.
     */
    @Nested
    class IgnoringCase {

        @Test
        void anUpperCasedNameIsTheSameType() {
            assertEquals(SupportedRecordingFile.JFR, SupportedRecordingFile.of("RUN.JFR"));
            assertEquals(SupportedRecordingFile.HEAP_DUMP, SupportedRecordingFile.of("HEAP.HPROF"));
            assertEquals(SupportedRecordingFile.HEAP_DUMP_GZ, SupportedRecordingFile.of("Heap.HProf.GZ"));
            assertEquals(SupportedRecordingFile.JFR_LZ4, SupportedRecordingFile.of("Run.Jfr.Lz4"));
        }

        /**
         * Asked of one type directly rather than through {@code of} — the door the session-finished
         * detector uses, and the reason the rule lives in {@code matches} rather than in {@code of}.
         */
        @Test
        void holdsWhenOneTypeIsAskedDirectly() {
            assertTrue(SupportedRecordingFile.HS_JVM_ERROR_LOG.matches("HS-JVM-ERR.LOG"));
            assertTrue(SupportedRecordingFile.JFR.matches(Path.of("/tmp/RUN.JFR")));
        }

        @Test
        void holdsForThePatternMatchersToo() {
            assertEquals(SupportedRecordingFile.ASPROF_TEMP, SupportedRecordingFile.of("RUN.JFR.1~"));
            assertEquals(SupportedRecordingFile.JVM_LOG, SupportedRecordingFile.of("GC.JVM-LOG"));
        }
    }

    /**
     * What a file is known by within its session. An id is not simply the name because a
     * recording gets renamed underneath its readers when the compression job reaches it.
     */
    @Nested
    class Ids {

        @Test
        void aRecordingDropsItsExtensionSoTheIdSurvivesCompression() {
            assertEquals("profile-20260220-120500",
                    SupportedRecordingFile.JFR.idOf(Path.of("s", "profile-20260220-120500.jfr")));
            assertEquals("profile-20260220-120500",
                    SupportedRecordingFile.JFR_LZ4.idOf(Path.of("s", "profile-20260220-120500.jfr.lz4")));
        }

        /**
         * Nothing renames these, so dropping the extension would buy nothing and would collide
         * two files of one session that differ only by it.
         */
        @Test
        void everythingElseKeepsItsWholeName() {
            assertEquals("service-app.log",
                    SupportedRecordingFile.APP_LOG.idOf(Path.of("s", "service-app.log")));
            assertEquals("heap.hprof",
                    SupportedRecordingFile.HEAP_DUMP.idOf(Path.of("s", "heap.hprof")));
            assertEquals("app.pprof",
                    SupportedRecordingFile.PPROF.idOf(Path.of("s", "app.pprof")));
            assertEquals("notes.txt",
                    SupportedRecordingFile.UNKNOWN.idOf(Path.of("s", "notes.txt")));
        }

        @Test
        void aRecordingAndItsArchiveAreOneId() {
            Path plain = Path.of("s", "profile-1.jfr");
            Path archive = Path.of("s", "profile-1.jfr.lz4");

            assertEquals(
                    SupportedRecordingFile.of(plain).idOf(plain),
                    SupportedRecordingFile.of(archive).idOf(archive));
        }
    }

    @Nested
    class Arguments {

        /**
         * Null used to reach the matcher and throw. Nothing is a file of any type, which is what an
         * absent name means.
         */
        @Test
        void aMissingNameIsNotAnyType() {
            assertFalse(SupportedRecordingFile.JFR.matches((String) null));
        }
    }
}
