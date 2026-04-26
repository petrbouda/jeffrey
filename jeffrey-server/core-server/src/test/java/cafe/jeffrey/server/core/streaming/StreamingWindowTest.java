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

package cafe.jeffrey.server.core.streaming;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class StreamingWindowTest {

    private static final Instant T1 = Instant.parse("2025-12-20T00:00:00Z");
    private static final Instant T2 = Instant.parse("2025-12-20T00:10:00Z");
    private static final Instant T3 = Instant.parse("2025-12-20T00:20:00Z");

    @Nested
    class Construction {

        @Test
        void bothBoundsSet() {
            var window = new StreamingWindow(T1, T3);
            assertEquals(T1, window.startTime());
            assertEquals(T3, window.endTime());
        }

        @Test
        void onlyStartSet() {
            var window = new StreamingWindow(T1, null);
            assertEquals(T1, window.startTime());
            assertNull(window.endTime());
        }

        @Test
        void onlyEndSet() {
            var window = new StreamingWindow(null, T3);
            assertNull(window.startTime());
            assertEquals(T3, window.endTime());
        }

        @Test
        void unbounded() {
            var window = StreamingWindow.UNBOUNDED;
            assertNull(window.startTime());
            assertNull(window.endTime());
        }

        @Test
        void startEqualsEndThrows() {
            assertThrows(IllegalArgumentException.class, () -> new StreamingWindow(T1, T1));
        }

        @Test
        void startAfterEndThrows() {
            assertThrows(IllegalArgumentException.class, () -> new StreamingWindow(T3, T1));
        }
    }

    @Nested
    class Contains {

        @Test
        void eventInsideWindow() {
            var window = new StreamingWindow(T1, T3);
            assertTrue(window.contains(T2));
        }

        @Test
        void eventBeforeStart() {
            var window = new StreamingWindow(T2, T3);
            assertFalse(window.contains(T1));
        }

        @Test
        void eventAfterEnd() {
            var window = new StreamingWindow(T1, T2);
            assertFalse(window.contains(T3));
        }

        @Test
        void eventAtExactStart() {
            var window = new StreamingWindow(T1, T3);
            assertTrue(window.contains(T1));
        }

        @Test
        void eventAtExactEnd() {
            var window = new StreamingWindow(T1, T3);
            assertTrue(window.contains(T3));
        }

        @Test
        void unboundedMatchesEverything() {
            assertTrue(StreamingWindow.UNBOUNDED.contains(T1));
            assertTrue(StreamingWindow.UNBOUNDED.contains(T3));
            assertTrue(StreamingWindow.UNBOUNDED.contains(Instant.EPOCH));
        }

        @Test
        void nullStartMatchesEarlyEvents() {
            var window = new StreamingWindow(null, T3);
            assertTrue(window.contains(T1));
            assertTrue(window.contains(Instant.EPOCH));
        }

        @Test
        void nullEndMatchesLateEvents() {
            var window = new StreamingWindow(T1, null);
            assertTrue(window.contains(T3));
            assertTrue(window.contains(Instant.parse("2099-01-01T00:00:00Z")));
        }
    }
}
