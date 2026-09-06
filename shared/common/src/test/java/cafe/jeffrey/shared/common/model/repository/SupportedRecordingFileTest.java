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
            assertEquals(SupportedRecordingFile.JVM_LOG, SupportedRecordingFile.of("SERVICE-JVM.LOG"));
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
