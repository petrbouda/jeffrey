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

import cafe.jeffrey.shared.common.model.RecordingEventSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportedFileTest {

    @Nested
    class Recognising {

        @Test
        void recordingsAndTheirCompressedForms() {
            assertEquals(SupportedFile.JFR, SupportedFile.of("run.jfr"));
            assertEquals(SupportedFile.JFR_LZ4, SupportedFile.of("run.jfr.lz4"));
            assertEquals(SupportedFile.HEAP_DUMP, SupportedFile.of("heap.hprof"));
            assertEquals(SupportedFile.HEAP_DUMP_GZ, SupportedFile.of("heap.hprof.gz"));
            assertEquals(SupportedFile.PPROF, SupportedFile.of("cpu.pprof"));
            assertEquals(SupportedFile.PPROF, SupportedFile.of("cpu.pb.gz"));
            assertEquals(SupportedFile.OTLP_PROFILE, SupportedFile.of("profiles.otlp"));
        }

        @Test
        void applicationLogsInTheirRotatedAndCompressedForms() {
            assertEquals(SupportedFile.APP_LOG, SupportedFile.of("service.log"));
            assertEquals(SupportedFile.APP_LOG, SupportedFile.of("service.log.1.gz"));
            assertEquals(SupportedFile.APP_LOG, SupportedFile.of("service.2026-09-13.log.gz"));
            assertEquals(SupportedFile.APP_LOG, SupportedFile.of("service-2026-09-13.1.log.zip"));
        }

        @Test
        void jvmLogsAndTheirRotatedForms() {
            assertEquals(SupportedFile.JVM_LOG, SupportedFile.of("gc.jvm-log"));
            assertEquals(SupportedFile.JVM_LOG, SupportedFile.of("gc.jvm-log.0"));
        }

        /**
         * The crash log ends in {@code .log} like an application log; it is the one name that relies on
         * being declared before {@code APP_LOG}, so the order is pinned here rather than left to reordering.
         */
        @Test
        void theCrashLogIsNotAnApplicationLog() {
            assertEquals(SupportedFile.HS_JVM_ERROR_LOG, SupportedFile.of("hs-jvm-err.log"));
        }

        /**
         * A compressed recording must not be read as an uncompressed one: the two are stored and
         * decoded differently, and {@code run.jfr.lz4} ends with neither {@code .jfr} nor anything
         * {@code JFR} would claim.
         */
        @Test
        void aCompressedRecordingIsNotThePlainOne() {
            assertFalse(SupportedFile.JFR.matches("run.jfr.lz4"));
            assertFalse(SupportedFile.HEAP_DUMP.matches("heap.hprof.gz"));
        }

        @Test
        void anythingElseIsUnknown() {
            assertEquals(SupportedFile.UNKNOWN, SupportedFile.of("notes.txt"));
            assertEquals(SupportedFile.UNKNOWN, SupportedFile.of("Main.java"));
            assertEquals(SupportedFile.UNKNOWN, SupportedFile.of("jfr"));
        }

        @Test
        void aPathIsJudgedByItsFileName() {
            assertEquals(
                    SupportedFile.JFR,
                    SupportedFile.of(Path.of("/var/recordings/run.jfr")));
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
            assertEquals(SupportedFile.JFR, SupportedFile.of("RUN.JFR"));
            assertEquals(SupportedFile.HEAP_DUMP, SupportedFile.of("HEAP.HPROF"));
            assertEquals(SupportedFile.HEAP_DUMP_GZ, SupportedFile.of("Heap.HProf.GZ"));
            assertEquals(SupportedFile.JFR_LZ4, SupportedFile.of("Run.Jfr.Lz4"));
        }

        /**
         * Asked of one type directly rather than through {@code of} — the door the session-finished
         * detector uses, and the reason the rule lives in {@code matches} rather than in {@code of}.
         */
        @Test
        void holdsWhenOneTypeIsAskedDirectly() {
            assertTrue(SupportedFile.HS_JVM_ERROR_LOG.matches("HS-JVM-ERR.LOG"));
            assertTrue(SupportedFile.JFR.matches(Path.of("/tmp/RUN.JFR")));
        }

        @Test
        void holdsForThePatternMatchersToo() {
            assertEquals(SupportedFile.ASPROF_TEMP, SupportedFile.of("RUN.JFR.1~"));
            assertEquals(SupportedFile.JVM_LOG, SupportedFile.of("GC.JVM-LOG"));
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
            assertFalse(SupportedFile.JFR.matches((String) null));
        }
    }

    /**
     * Everything Jeffrey knows about a type is declared on the type: the hub and Microscope ask
     * these facts instead of keeping lists of their own.
     */
    @Nested
    class DeclaredFacts {

        @Test
        void onlyTheJfrFormsAreChunksOfARecording() {
            for (SupportedFile file : SupportedFile.values()) {
                boolean expected = file == SupportedFile.JFR || file == SupportedFile.JFR_LZ4;
                assertEquals(expected, file.isRecordingChunk(), file.name());
            }
            assertEquals(List.of("jfr.lz4", "jfr"), SupportedFile.recordingChunkExtensions());
        }

        @Test
        void onlyTheProfilerCacheIsTransient() {
            for (SupportedFile file : SupportedFile.values()) {
                assertEquals(file == SupportedFile.ASPROF_TEMP, file.isTransient(), file.name());
            }
        }

        @Test
        void theHubWritesOnlyTheCompressedChunk() {
            for (SupportedFile file : SupportedFile.values()) {
                assertEquals(file == SupportedFile.JFR_LZ4, file.isCompressedByHub(), file.name());
            }
        }

        @Test
        void theCrashLogIsTheOneCrashSignal() {
            for (SupportedFile file : SupportedFile.values()) {
                assertEquals(file == SupportedFile.HS_JVM_ERROR_LOG, file.isCrashSignal(), file.name());
            }
        }

        @Test
        void profileRecordingsAreSearchedCompressedChunkFirst() {
            assertEquals(
                    List.of(SupportedFile.JFR_LZ4, SupportedFile.JFR, SupportedFile.PPROF, SupportedFile.OTLP_PROFILE),
                    SupportedFile.profileRecordings());
        }

        @Test
        void theNameAloneDecidesTheEventSourceOnlyForNonJfrFormats() {
            assertEquals(Optional.of(RecordingEventSource.HEAP_DUMP), SupportedFile.HEAP_DUMP.eventSource());
            assertEquals(Optional.of(RecordingEventSource.HEAP_DUMP), SupportedFile.HEAP_DUMP_GZ.eventSource());
            assertEquals(Optional.of(RecordingEventSource.PPROF), SupportedFile.PPROF.eventSource());
            assertEquals(Optional.of(RecordingEventSource.OPEN_TELEMETRY), SupportedFile.OTLP_PROFILE.eventSource());
            assertEquals(Optional.empty(), SupportedFile.JFR.eventSource());
            assertEquals(Optional.empty(), SupportedFile.UNKNOWN.eventSource());
        }

        @Test
        void statisticsBucketsFollowTheType() {
            assertEquals(StatsCategory.JFR, SupportedFile.JFR.statsCategory());
            assertEquals(StatsCategory.JFR, SupportedFile.JFR_LZ4.statsCategory());
            assertEquals(StatsCategory.HEAP_DUMP, SupportedFile.HEAP_DUMP.statsCategory());
            assertEquals(StatsCategory.HEAP_DUMP, SupportedFile.HEAP_DUMP_GZ.statsCategory());
            assertEquals(StatsCategory.LOG, SupportedFile.JVM_LOG.statsCategory());
            assertEquals(StatsCategory.APP_LOG, SupportedFile.APP_LOG.statsCategory());
            assertEquals(StatsCategory.ERROR_LOG, SupportedFile.HS_JVM_ERROR_LOG.statsCategory());
            assertEquals(StatsCategory.OTHER, SupportedFile.PERF_COUNTERS.statsCategory());
            assertEquals(StatsCategory.OTHER, SupportedFile.PPROF.statsCategory());
            assertEquals(StatsCategory.OTHER, SupportedFile.UNKNOWN.statsCategory());
        }
    }

    /**
     * {@code UNKNOWN} is the answer when nothing matched, not a type that matches everything: it
     * recognises no name, declares nothing, and is otherwise a file like any other.
     */
    @Nested
    class TheFallback {

        @Test
        void recognisesNoName() {
            assertFalse(SupportedFile.UNKNOWN.matches("anything.bin"));
            assertFalse(SupportedFile.UNKNOWN.matches("run.jfr"));
        }

        @Test
        void declaresNothing() {
            assertFalse(SupportedFile.UNKNOWN.isRecordingChunk());
            assertFalse(SupportedFile.UNKNOWN.isTransient());
            assertFalse(SupportedFile.UNKNOWN.isProfileRecording());
            assertEquals(null, SupportedFile.UNKNOWN.fileExtension());
        }

        @Test
        void isWhatAnUnknownTypeNameResolvesTo() {
            assertEquals(SupportedFile.UNKNOWN, SupportedFile.ofType("NOT_A_TYPE"));
            assertEquals(SupportedFile.HEAP_DUMP, SupportedFile.ofType("HEAP_DUMP"));
        }
    }
}
