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

package cafe.jeffrey.shared.common;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The UTC offset a recording's name carries: async-profiler writes {@code %t} in local time, so
 * the provisioner writes the offset right after it, and every reader reads the name in it.
 */
class JeffreyLayoutTest {

    /** A file share refuses {@code :} in a name, so an offset is written without it. */
    @Nested
    class RecordingTemplate {

        @Test
        void carriesTheOffsetRightAfterTheTimestamp() {
            assertEquals("rec-%t+0100.jfr", JeffreyLayout.recordingTemplate(ZoneOffset.ofHours(1)));
            assertEquals("rec-%t+0000.jfr", JeffreyLayout.recordingTemplate(ZoneOffset.UTC));
            assertEquals("rec-%t+0530.jfr", JeffreyLayout.recordingTemplate(ZoneOffset.ofHoursMinutes(5, 30)));
            assertEquals("rec-%t-0330.jfr", JeffreyLayout.recordingTemplate(ZoneOffset.ofHoursMinutes(-3, -30)));
        }
    }

    @Nested
    class RecordingStart {

        @Test
        void readsTheNameAtItsOffset() {
            assertEquals(Optional.of(Instant.parse("2026-10-27T07:55:00Z")),
                    JeffreyLayout.recordingStart("rec-20261027-085500+0100"));
            assertEquals(Optional.of(Instant.parse("2026-10-27T12:25:00Z")),
                    JeffreyLayout.recordingStart("rec-20261027-085500-0330"));
        }

        @Test
        void readsANameWithoutAnOffsetAsUtc() {
            assertEquals(Optional.of(Instant.parse("2026-10-27T08:55:00Z")),
                    JeffreyLayout.recordingStart("rec-20261027-085500"));
        }

        /** What the template writes, the reader reads back. */
        @Test
        void readsWhatTheTemplateWrites() {
            String id = JeffreyLayout.recordingTemplate(ZoneOffset.ofHours(2))
                    .replace(JeffreyLayout.TIMESTAMP_PLACEHOLDER, "20260701-120000")
                    .replace(".jfr", "");

            assertEquals(Optional.of(Instant.parse("2026-07-01T10:00:00Z")), JeffreyLayout.recordingStart(id));
        }

        @Test
        void aNameOutsideTheConventionHasNoStart() {
            assertEquals(Optional.empty(), JeffreyLayout.recordingStart("profile-20261027-085500+0100"));
            assertEquals(Optional.empty(), JeffreyLayout.recordingStart("rec-not-a-timestamp"));
            assertEquals(Optional.empty(), JeffreyLayout.recordingStart("gc"));
        }
    }
}
