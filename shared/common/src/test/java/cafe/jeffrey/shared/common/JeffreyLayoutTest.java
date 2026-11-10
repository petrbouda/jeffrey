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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The UTC offset and the file number a recording's name carries: async-profiler writes {@code %t}
 * in local time, so the provisioner writes the offset right after it, and every reader reads the
 * name in it; the number keeps names unique, and orders a session's chunks, when local time
 * repeats an hour.
 */
class JeffreyLayoutTest {

    /** A file share refuses {@code :} in a name, so an offset is written without it. */
    @Nested
    class RecordingTemplate {

        @Test
        void carriesTheOffsetRightAfterTheTimestampThenTheFileNumber() {
            assertEquals("rec-%t+0100_%n.jfr", JeffreyLayout.recordingTemplate(ZoneOffset.ofHours(1)));
            assertEquals("rec-%t+0000_%n.jfr", JeffreyLayout.recordingTemplate(ZoneOffset.UTC));
            assertEquals("rec-%t+0530_%n.jfr", JeffreyLayout.recordingTemplate(ZoneOffset.ofHoursMinutes(5, 30)));
            assertEquals("rec-%t-0330_%n.jfr", JeffreyLayout.recordingTemplate(ZoneOffset.ofHoursMinutes(-3, -30)));
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
        void readsANameWithAFileNumberAtItsOffset() {
            assertEquals(Optional.of(Instant.parse("2026-10-27T07:55:00Z")),
                    JeffreyLayout.recordingStart("rec-20261027-085500+0100_7"));
            assertEquals(Optional.of(Instant.parse("2026-10-27T12:25:00Z")),
                    JeffreyLayout.recordingStart("rec-20261027-085500-0330_12"));
            assertEquals(Optional.of(Instant.parse("2026-10-27T08:55:00Z")),
                    JeffreyLayout.recordingStart("rec-20261027-085500_3"));
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
                    .replace(JeffreyLayout.SEQUENCE_PLACEHOLDER, "41")
                    .replace(".jfr", "");

            assertEquals(Optional.of(Instant.parse("2026-07-01T10:00:00Z")), JeffreyLayout.recordingStart(id));
            assertEquals(OptionalLong.of(41), JeffreyLayout.recordingSequence(id));
        }

        @Test
        void aNameOutsideTheConventionHasNoStart() {
            assertEquals(Optional.empty(), JeffreyLayout.recordingStart("profile-20261027-085500+0100"));
            assertEquals(Optional.empty(), JeffreyLayout.recordingStart("rec-not-a-timestamp"));
            assertEquals(Optional.empty(), JeffreyLayout.recordingStart("gc"));
        }
    }

    @Nested
    class RecordingSequence {

        @Test
        void readsTheFileNumberAtTheEndOfTheId() {
            assertEquals(OptionalLong.of(0), JeffreyLayout.recordingSequence("rec-20261027-085500+0100_0"));
            assertEquals(OptionalLong.of(1234), JeffreyLayout.recordingSequence("rec-20261027-085500-0330_1234"));
        }

        /** A negative offset ends in digits too: the separator is what tells it from a number. */
        @Test
        void aNameWithoutANumberHasNone() {
            assertEquals(OptionalLong.empty(), JeffreyLayout.recordingSequence("rec-20261027-085500-0330"));
            assertEquals(OptionalLong.empty(), JeffreyLayout.recordingSequence("rec-20261027-085500+0100"));
            assertEquals(OptionalLong.empty(), JeffreyLayout.recordingSequence("rec-20261027-085500"));
        }

        @Test
        void aNameOutsideTheConventionHasNone() {
            assertEquals(OptionalLong.empty(), JeffreyLayout.recordingSequence("gc_7"));
            assertEquals(OptionalLong.empty(), JeffreyLayout.recordingSequence("rec-x_99999999999999999999999"));
        }
    }

    /**
     * One session's chunks in the order they were written. Daylight saving ends at 03:00 local in
     * Prague: the clocks go back to 02:00 while the name keeps the session's +0200, so the hour
     * after the switch reads one hour earlier than the hour before it.
     */
    @Nested
    class RecordingOrder {

        private record Chunk(String id) {

            Instant start() {
                return JeffreyLayout.recordingStart(id).orElseThrow();
            }
        }

        private static final Comparator<Chunk> ORDER = JeffreyLayout.recordingOrder(Chunk::id, Chunk::start);

        @Test
        void followsTheFileNumberWhenTheClockGoesBack() {
            Chunk beforeTheSwitch = new Chunk("rec-20261025-024500+0200_10");
            Chunk afterTheSwitch = new Chunk("rec-20261025-020000+0200_11");
            Chunk sameTimeAnHourLater = new Chunk("rec-20261025-024500+0200_14");

            assertTrue(afterTheSwitch.start().isBefore(beforeTheSwitch.start()),
                    "the hour after the switch reads earlier than the one before it");
            assertEquals(List.of(beforeTheSwitch, afterTheSwitch, sameTimeAnHourLater),
                    List.of(sameTimeAnHourLater, afterTheSwitch, beforeTheSwitch).stream().sorted(ORDER).toList());
        }

        /** Names written before the number was added, or by a custom template: by their start. */
        @Test
        void ordersChunksWithoutANumberByTheirStart() {
            Chunk first = new Chunk("rec-20261027-085500+0100");
            Chunk second = new Chunk("rec-20261027-091000+0100");

            assertEquals(List.of(first, second), List.of(second, first).stream().sorted(ORDER).toList());
        }
    }
}
