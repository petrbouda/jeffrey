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

package cafe.jeffrey.hub.core.project.repository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubManagedFileTest {

    @Nested
    class Recognising {

        @Test
        void aRecordingAndItsArchive() {
            assertEquals(Optional.of(HubManagedFile.JFR), HubManagedFile.of("profile-20260220-120500.jfr"));
            assertEquals(Optional.of(HubManagedFile.JFR_LZ4), HubManagedFile.of("profile-20260220-120500.jfr.lz4"));
        }

        /**
         * The profiler writes the extension in lower case and nothing else names a recording.
         * Matched loosely and stripped exactly, an upper-cased name was a JFR whose id changed
         * from {@code PROFILE.JFR} to {@code PROFILE.JFR.lz4} when it was compressed.
         */
        @Test
        void anUpperCasedNameIsNotARecording() {
            assertEquals(Optional.empty(), HubManagedFile.of("PROFILE.JFR"));
        }

        /**
         * A log, a dump, a pprof profile and the profiler's own scratch file are all files the hub
         * lists and serves without naming: it does nothing to them, so it has nothing to say
         * about them. Microscope, which reads them, classifies the name on its side.
         */
        @Test
        void hasNoNameForAnythingElse() {
            assertTrue(HubManagedFile.of("service-app.log").isEmpty());
            assertTrue(HubManagedFile.of("heap.hprof").isEmpty());
            assertTrue(HubManagedFile.of("app.pprof").isEmpty());
            assertTrue(HubManagedFile.of("profile-20260220-120500.jfr.1~").isEmpty());
        }

        @Test
        void aMissingNameIsNotAnyType() {
            assertFalse(HubManagedFile.JFR.matches(null));
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
                    HubManagedFile.JFR.idOf(Path.of("s", "profile-20260220-120500.jfr")));
            assertEquals("profile-20260220-120500",
                    HubManagedFile.JFR_LZ4.idOf(Path.of("s", "profile-20260220-120500.jfr.lz4")));
        }

        @Test
        void aRecordingAndItsArchiveAreOneId() {
            Path plain = Path.of("s", "profile-1.jfr");
            Path archive = Path.of("s", "profile-1.jfr.lz4");

            assertEquals(
                    HubManagedFile.of(plain).orElseThrow().idOf(plain),
                    HubManagedFile.of(archive).orElseThrow().idOf(archive));
        }
    }
}
