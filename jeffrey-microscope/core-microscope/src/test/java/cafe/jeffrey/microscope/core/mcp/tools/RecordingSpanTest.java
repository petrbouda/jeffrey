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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingSpanTest {

    private static final Instant START = Instant.parse("2026-03-01T12:00:00Z");
    private static final long START_MS = START.toEpochMilli();
    private static final long END_MS = START_MS + 60_000;

    private static ProfileInfo info(String id, Instant start, Instant end) {
        return new ProfileInfo(id, "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                start, end, START, true, false, "recording-" + id);
    }

    private static RecordingSpan span() {
        return RecordingSpan.require(info("p-1", START, START.plusSeconds(60)));
    }

    @Nested
    class Of {

        @Test
        void readsTheRecordingsBoundsAsEpochMilliseconds() {
            RecordingSpan span = span();

            assertEquals("p-1", span.profileId());
            assertEquals(START_MS, span.startEpochMs());
            assertEquals(END_MS, span.endEpochMs());
            assertEquals(60_000, span.durationMs());
        }

        @Test
        void hasNoSpanForARecordingWithoutTimestamps() {
            assertTrue(RecordingSpan.of(info("p-1", null, null)).isEmpty());
        }

        @Test
        void hasNoSpanForARecordingThatRanForNoTime() {
            ProfileInfo zero = info("p-1", START, START);

            assertTrue(RecordingSpan.of(zero).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> RecordingSpan.require(zero));
        }

        /**
         * Shorter than a millisecond is no span on the millisecond base: it is reported as none, never
         * thrown from the span's own invariant.
         */
        @Test
        void hasNoSpanForARecordingShorterThanAMillisecond() {
            ProfileInfo instant = info("p-1", START, START.plusNanos(500_000));

            assertTrue(RecordingSpan.of(instant).isEmpty());
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> RecordingSpan.require(instant));
            assertTrue(thrown.getMessage().contains("omit startEpochMs and endEpochMs"), thrown.getMessage());
        }

        /** A window cannot be placed on a recording that never said when it ran. */
        @Test
        void refusesToPlaceAWindowOnARecordingWithoutTimestamps() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> RecordingSpan.require(info("p-1", START, null)));

            assertTrue(thrown.getMessage().contains("p-1"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("omit startEpochMs and endEpochMs"), thrown.getMessage());
        }
    }

    @Nested
    class Window {

        @Test
        void anOmittedBoundIsTheRecordingsOwn() {
            assertEquals(new EpochWindow(START_MS, END_MS), span().window(null, null));
            assertEquals(new EpochWindow(START_MS + 10_000, END_MS), span().window(START_MS + 10_000, null));
            assertEquals(new EpochWindow(START_MS, START_MS + 5_000), span().window(null, START_MS + 5_000));
        }

        @Test
        void acceptsTheRecordingsExactBounds() {
            assertEquals(new EpochWindow(START_MS, END_MS), span().window(START_MS, END_MS));
        }

        /**
         * The refusal is the correction: it names the recording's own span on the same time base, so the
         * caller can put the window back inside it without another call.
         */
        @Test
        void refusesAStartBeforeTheRecordingAndNamesItsSpan() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> span().window(START_MS - 1, null));

            assertTrue(thrown.getMessage().contains("startEpochMs=" + (START_MS - 1)), thrown.getMessage());
            assertTrue(thrown.getMessage().contains(START_MS + ".." + END_MS), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("UTC epoch ms"), thrown.getMessage());
        }

        @Test
        void refusesAnEndAfterTheRecording() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> span().window(START_MS, END_MS + 1));

            assertTrue(thrown.getMessage().contains("endEpochMs=" + (END_MS + 1)), thrown.getMessage());
            assertTrue(thrown.getMessage().contains(START_MS + ".." + END_MS), thrown.getMessage());
        }

        /** Offsets from the recording start are what the old inputs took; they are now refused, not misread. */
        @Test
        void refusesAnOffsetPassedWhereAnEpochIsExpected() {
            assertThrows(IllegalArgumentException.class, () -> span().window(1_000L, 2_000L));
        }

        @Test
        void refusesAWindowThatEndsWhereItStarts() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> span().window(START_MS + 1_000, START_MS + 1_000));

            assertTrue(thrown.getMessage().contains("endEpochMs must be greater than startEpochMs"),
                    thrown.getMessage());
        }

        @Test
        void convertsAWindowToTheOffsetsTheManagersRead() {
            RelativeTimeRange offsets = span().offsets(new EpochWindow(START_MS + 1_000, START_MS + 3_500));

            assertEquals(Duration.ofMillis(1_000), offsets.start());
            assertEquals(Duration.ofMillis(3_500), offsets.end());
        }
    }

    /** A comparison applies one window at the same offset into both recordings. */
    @Nested
    class SameOffset {

        private final RecordingSpan baseline = RecordingSpan.require(
                info("b-1", START.minusSeconds(3_600), START.minusSeconds(3_600).plusSeconds(30)));

        @Test
        void placesTheOffsetsOnTheOtherRecording() {
            long baselineStart = START_MS - 3_600_000;

            assertEquals(new EpochWindow(baselineStart + 1_000, baselineStart + 2_000),
                    baseline.windowAt(1_000, 2_000L));
        }

        @Test
        void anOpenEndIsTheOtherRecordingsOwnEnd() {
            assertEquals(baseline.endEpochMs(), baseline.windowAt(1_000, null).endEpochMs());
        }

        @Test
        void refusesAnOffsetPastTheOtherRecordingsEndAndSaysHowLongItIs() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> baseline.windowAt(1_000, 45_000L));

            assertTrue(thrown.getMessage().contains("b-1"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("30000 ms long"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("same offset"), thrown.getMessage());
        }
    }
}
