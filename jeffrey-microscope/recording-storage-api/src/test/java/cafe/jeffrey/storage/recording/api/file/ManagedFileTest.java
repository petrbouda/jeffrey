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

package cafe.jeffrey.storage.recording.api.file;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedFileTest {

    @Nested
    class Recognising {

        @Test
        void recordingsAndTheirCompressedForms() {
            assertEquals(ManagedFile.JFR, ManagedFile.of("run.jfr"));
            assertEquals(ManagedFile.JFR_LZ4, ManagedFile.of("run.jfr.lz4"));
            assertEquals(ManagedFile.HEAP_DUMP, ManagedFile.of("heap.hprof"));
            assertEquals(ManagedFile.HEAP_DUMP_GZ, ManagedFile.of("heap.hprof.gz"));
            assertEquals(ManagedFile.PPROF, ManagedFile.of("cpu.pprof"));
            assertEquals(ManagedFile.PPROF, ManagedFile.of("cpu.pb.gz"));
            assertEquals(ManagedFile.OTLP_PROFILE, ManagedFile.of("profiles.otlp"));
        }

        @Test
        void applicationLogsInTheirRotatedAndCompressedForms() {
            assertEquals(ManagedFile.APP_LOG, ManagedFile.of("service.log"));
            assertEquals(ManagedFile.APP_LOG, ManagedFile.of("service.log.1.gz"));
            assertEquals(ManagedFile.APP_LOG, ManagedFile.of("service.2026-09-13.log.gz"));
            assertEquals(ManagedFile.APP_LOG, ManagedFile.of("service-2026-09-13.1.log.zip"));
        }

        @Test
        void jvmLogsAndTheirRotatedForms() {
            assertEquals(ManagedFile.JVM_LOG, ManagedFile.of("gc.jvm-log"));
            assertEquals(ManagedFile.JVM_LOG, ManagedFile.of("gc.jvm-log.0"));
        }

        /**
         * The crash log ends in {@code .log} like an application log; it is the one name that relies on
         * being declared before {@code APP_LOG}, so the order is pinned here rather than left to reordering.
         */
        @Test
        void theCrashLogIsNotAnApplicationLog() {
            assertEquals(ManagedFile.HS_JVM_ERROR_LOG, ManagedFile.of("hs-jvm-err.log"));
        }

        /**
         * The JVM's own default spelling ends in {@code .log} too, so without the matcher being
         * asked first the crash file was filed as an application log — listed under the wrong
         * badge, and named as one by {@code hubs_files} when an agent went looking for the crash.
         */
        @Test
        void theJvmDefaultCrashLogIsNotAnApplicationLogEither() {
            assertEquals(ManagedFile.HS_JVM_ERROR_LOG, ManagedFile.of("hs_err_pid123.log"));
            assertEquals(ManagedFile.HS_JVM_ERROR_LOG, ManagedFile.of("HS_ERR_PID123.LOG"));
        }

        /**
         * A compressed recording must not be read as an uncompressed one: the two are stored and
         * decoded differently, and {@code run.jfr.lz4} ends with neither {@code .jfr} nor anything
         * {@code JFR} would claim.
         */
        @Test
        void aCompressedRecordingIsNotThePlainOne() {
            assertFalse(ManagedFile.JFR.matches("run.jfr.lz4"));
            assertFalse(ManagedFile.HEAP_DUMP.matches("heap.hprof.gz"));
        }

        @Test
        void anythingElseIsUnknown() {
            assertEquals(ManagedFile.UNKNOWN, ManagedFile.of("notes.txt"));
            assertEquals(ManagedFile.UNKNOWN, ManagedFile.of("Main.java"));
            assertEquals(ManagedFile.UNKNOWN, ManagedFile.of("jfr"));
        }

        @Test
        void aPathIsJudgedByItsFileName() {
            assertEquals(
                    ManagedFile.JFR,
                    ManagedFile.of(Path.of("/var/recordings/run.jfr")));
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
            assertEquals(ManagedFile.JFR, ManagedFile.of("RUN.JFR"));
            assertEquals(ManagedFile.HEAP_DUMP, ManagedFile.of("HEAP.HPROF"));
            assertEquals(ManagedFile.HEAP_DUMP_GZ, ManagedFile.of("Heap.HProf.GZ"));
            assertEquals(ManagedFile.JFR_LZ4, ManagedFile.of("Run.Jfr.Lz4"));
        }

        /**
         * Asked of one type directly rather than through {@code of} — the door the session-finished
         * detector uses, and the reason the rule lives in {@code matches} rather than in {@code of}.
         */
        @Test
        void holdsWhenOneTypeIsAskedDirectly() {
            assertTrue(ManagedFile.HS_JVM_ERROR_LOG.matches("HS-JVM-ERR.LOG"));
            assertTrue(ManagedFile.JFR.matches(Path.of("/tmp/RUN.JFR")));
        }

        @Test
        void holdsForThePatternMatchersToo() {
            assertEquals(ManagedFile.ASPROF_TEMP, ManagedFile.of("RUN.JFR.1~"));
            assertEquals(ManagedFile.JVM_LOG, ManagedFile.of("GC.JVM-LOG"));
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
            assertFalse(ManagedFile.JFR.matches((String) null));
        }
    }
}
