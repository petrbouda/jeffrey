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

package cafe.jeffrey.profile.thread;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadBandsTest {

    /**
     * A four-second recording divided into 4000 buckets — one bucket is a round millisecond, which
     * keeps the offsets in these tests readable.
     */
    private static final Duration RECORDING = Duration.ofSeconds(4);
    private static final long BUCKET_NANOS = Duration.ofMillis(1).toNanos();

    private final ThreadBands bands = ThreadBands.forRecording(RECORDING);

    private static ThreadPeriod event(long startNanos, long widthNanos) {
        return new ThreadPeriod(startNanos, widthNanos, 1);
    }

    @Nested
    class EventsTooCloseToDrawApart {

        /**
         * The case behind the oversized responses: thousands of short writes in a burst are a single
         * rectangle on screen, and must cost a single band on the wire.
         */
        @Test
        void collapseIntoOneBandThatKeepsTheirCount() {
            List<ThreadPeriod> burst = IntStream.range(0, 5000)
                    .mapToObj(i -> event(i * 1_000L, 500))
                    .toList();

            List<ThreadPeriod> merged = bands.merge(burst);

            assertEquals(1, merged.size(), "A burst is one rectangle, so it must be one band");
            assertEquals(5000, merged.getFirst().eventCount(), "The band stands in for every event it absorbed");
            assertEquals(0, merged.getFirst().startOffset());
        }

        @Test
        void areMergedWhenTheyOverlap() {
            List<ThreadPeriod> merged = bands.merge(List.of(
                    event(0, BUCKET_NANOS * 3),
                    event(BUCKET_NANOS, BUCKET_NANOS * 3)));

            assertEquals(1, merged.size());
            assertEquals(BUCKET_NANOS * 4, merged.getFirst().width(),
                    "The band spans to the furthest end of the events it covers");
            assertEquals(2, merged.getFirst().eventCount());
        }
    }

    @Nested
    class EventsFarEnoughApart {

        @Test
        void staySeparateBands() {
            List<ThreadPeriod> merged = bands.merge(List.of(
                    event(0, 100),
                    event(BUCKET_NANOS * 50, 100),
                    event(BUCKET_NANOS * 100, 100)));

            assertEquals(3, merged.size());
            assertTrue(merged.stream().allMatch(band -> band.eventCount() == 1));
        }

        /**
         * A gap wider than one bucket is a visible pixel of nothing on the canvas, so the events on
         * either side of it stay distinguishable.
         */
        @Test
        void keepTheirGapWhenItSpansMoreThanOneBucket() {
            List<ThreadPeriod> merged = bands.merge(List.of(
                    event(0, 1),
                    event(BUCKET_NANOS * 2 + 1, 1)));

            assertEquals(2, merged.size());
        }
    }

    @Nested
    class Boundaries {

        @Test
        void leaveShortInputUntouched() {
            assertTrue(bands.merge(List.of()).isEmpty());

            ThreadPeriod single = event(42, 7);
            List<ThreadPeriod> merged = bands.merge(List.of(single));
            assertEquals(1, merged.size());
            assertSame(single, merged.getFirst());
        }

        /**
         * A profile with no measured duration must not divide by zero — it falls back to the finest
         * possible bucket, which merges only genuinely touching events.
         */
        @Test
        void handleARecordingWithoutDuration() {
            ThreadBands degenerate = ThreadBands.forRecording(Duration.ZERO);

            List<ThreadPeriod> merged = degenerate.merge(List.of(event(0, 1), event(10, 1)));

            assertEquals(2, merged.size());
        }

        @Test
        void neverProduceAZeroWidthBand() {
            List<ThreadPeriod> merged = bands.merge(List.of(event(0, 1), event(1, 1)));

            assertEquals(1, merged.size());
            assertTrue(merged.getFirst().width() > 0);
        }
    }
}
