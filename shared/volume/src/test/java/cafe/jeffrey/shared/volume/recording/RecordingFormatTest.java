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

package cafe.jeffrey.shared.volume.recording;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingFormatTest {

    @Nested
    class Recognising {

        @Test
        void aRecordingAndItsArchive() {
            assertEquals(Optional.of(RecordingFormat.JFR), RecordingFormat.of("profile-20260220-120500.jfr"));
            assertEquals(Optional.of(RecordingFormat.JFR_LZ4), RecordingFormat.of("profile-20260220-120500.jfr.lz4"));
        }

        /**
         * The profiler writes the extension in lower case and nothing else names a recording.
         * Matched loosely and stripped exactly, an upper-cased name was a JFR whose id changed
         * from {@code PROFILE.JFR} to {@code PROFILE.JFR.lz4} when it was compressed.
         */
        @Test
        void anUpperCasedNameIsNotARecording() {
            assertEquals(Optional.empty(), RecordingFormat.of("PROFILE.JFR"));
        }

        /**
         * A log, a dump, a pprof profile and the profiler's own scratch file are all files the
         * volume lists and serves without naming: nothing is done to them, so the layout has
         * nothing to say about them. Whoever reads them classifies the name on its side.
         */
        @Test
        void hasNoNameForAnythingElse() {
            assertTrue(RecordingFormat.of("service-app.log").isEmpty());
            assertTrue(RecordingFormat.of("heap.hprof").isEmpty());
            assertTrue(RecordingFormat.of("app.pprof").isEmpty());
            assertTrue(RecordingFormat.of("profile-20260220-120500.jfr.1~").isEmpty());
        }

        @Test
        void aMissingNameIsNotAnyType() {
            assertFalse(RecordingFormat.JFR.matches(null));
        }
    }

    /**
     * What a recording is known by within its session. Not simply the name, because the
     * compression job renames the file underneath its readers.
     */
    @Nested
    class Ids {

        @Test
        void aRecordingDropsItsExtensionSoTheIdSurvivesCompression() {
            assertEquals("profile-20260220-120500",
                    RecordingFormat.JFR.idOf("profile-20260220-120500.jfr"));
            assertEquals("profile-20260220-120500",
                    RecordingFormat.JFR_LZ4.idOf("profile-20260220-120500.jfr.lz4"));
        }

        @Test
        void aRecordingAndItsArchiveAreOneId() {
            String plain = "profile-1.jfr";
            String archive = "profile-1.jfr.lz4";

            assertEquals(
                    RecordingFormat.of(plain).orElseThrow().idOf(plain),
                    RecordingFormat.of(archive).orElseThrow().idOf(archive));
        }
    }

    @Nested
    class Archives {

        @Test
        void theLz4ArchiveIsTheOnlyFormThatCountsAsOne() {
            List<RecordingFormat> archives = Stream.of(RecordingFormat.values())
                    .filter(RecordingFormat::isArchive)
                    .toList();

            assertEquals(List.of(RecordingFormat.JFR_LZ4), archives);
        }

        /** Mid-compression a recording and its archive share an id; the archive is the one that stays. */
        @Test
        void ofTwoFilesSharingAnIdTheArchiveIsServedWhicheverIsListedFirst() {
            BinaryOperator<String> pick = RecordingFormat.theOneThatStays(name -> name);

            assertEquals("profile-1.jfr.lz4", pick.apply("profile-1.jfr.lz4", "profile-1.jfr"));
            assertEquals("profile-1.jfr.lz4", pick.apply("profile-1.jfr", "profile-1.jfr.lz4"));
        }
    }
}
