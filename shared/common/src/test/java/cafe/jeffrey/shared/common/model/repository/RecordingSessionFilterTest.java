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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingSessionFilterTest {

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
    private static final Instant ONE_HOUR_AGO = NOW.minus(Duration.ofHours(1));

    private static RecordingSession session(String id, Instant createdAt, Instant finishedAt) {
        RecordingStatus status = finishedAt != null ? RecordingStatus.FINISHED : RecordingStatus.ACTIVE;
        return new RecordingSession(id, id, "inst-1", createdAt, finishedAt, status, null, null, List.of(), false);
    }

    private static List<String> ids(List<RecordingSession> sessions) {
        return sessions.stream().map(RecordingSession::id).toList();
    }

    @Nested
    class Window {

        private final RecordingSessionFilter lastHour = new RecordingSessionFilter(ONE_HOUR_AGO, NOW, null, 0);

        @Test
        void stillRunningSessionStartedLongAgoOverlapsTheWindow() {
            RecordingSession running = session("running", NOW.minus(Duration.ofHours(3)), null);

            assertTrue(lastHour.matches(running));
        }

        @Test
        void sessionFinishedInsideTheWindowMatches() {
            RecordingSession finished = session("finished", NOW.minus(Duration.ofHours(3)), NOW.minus(Duration.ofMinutes(30)));

            assertTrue(lastHour.matches(finished));
        }

        @Test
        void sessionStartedAndFinishedInsideTheWindowMatches() {
            RecordingSession inside = session("inside", NOW.minus(Duration.ofMinutes(50)), NOW.minus(Duration.ofMinutes(10)));

            assertTrue(lastHour.matches(inside));
        }

        @Test
        void sessionFinishedBeforeTheWindowOpenedDoesNotMatch() {
            RecordingSession old = session("old", NOW.minus(Duration.ofHours(5)), NOW.minus(Duration.ofHours(2)));

            assertFalse(lastHour.matches(old));
        }

        @Test
        void sessionStartedAfterTheWindowClosedDoesNotMatch() {
            RecordingSession future = session("future", NOW.plus(Duration.ofMinutes(1)), null);

            assertFalse(lastHour.matches(future));
        }

        @Test
        void boundsAreInclusive() {
            RecordingSession finishedOnOpen = session("on-open", NOW.minus(Duration.ofHours(5)), ONE_HOUR_AGO);
            RecordingSession startedOnClose = session("on-close", NOW, null);

            assertTrue(lastHour.matches(finishedOnOpen));
            assertTrue(lastHour.matches(startedOnClose));
        }

        @Test
        void openEndedWindowOnlyChecksTheSetBound() {
            RecordingSessionFilter since = new RecordingSessionFilter(ONE_HOUR_AGO, null, null, 0);
            RecordingSessionFilter until = new RecordingSessionFilter(null, ONE_HOUR_AGO, null, 0);
            RecordingSession recent = session("recent", NOW.minus(Duration.ofMinutes(5)), null);
            RecordingSession old = session("old", NOW.minus(Duration.ofHours(5)), NOW.minus(Duration.ofHours(2)));

            assertTrue(since.matches(recent));
            assertFalse(since.matches(old));
            assertFalse(until.matches(recent));
            assertTrue(until.matches(old));
        }

        @Test
        void activeWithinLastLeavesTheEndOpen() {
            RecordingSessionFilter filter = RecordingSessionFilter.activeWithinLast(Duration.ofHours(1), NOW);

            assertEquals(ONE_HOUR_AGO, filter.activeFrom());
            assertEquals(null, filter.activeTo());
            // A session that starts after the caller read its clock is still picked up.
            assertTrue(filter.matches(session("later", NOW.plus(Duration.ofSeconds(5)), null)));
        }
    }

    @Nested
    class Status {

        @Test
        void nullStatusMatchesEveryStatus() {
            RecordingSessionFilter any = new RecordingSessionFilter(null, null, null, 0);

            assertTrue(any.matches(session("active", NOW, null)));
            assertTrue(any.matches(session("finished", NOW, NOW)));
        }

        @Test
        void statusRestrictsToThatStatusOnly() {
            RecordingSessionFilter activeOnly = RecordingSessionFilter.ALL.withStatus(RecordingStatus.ACTIVE);

            assertTrue(activeOnly.matches(session("active", NOW, null)));
            assertFalse(activeOnly.matches(session("finished", NOW, NOW)));
        }
    }

    @Nested
    class Apply {

        private final List<RecordingSession> newestFirst = List.of(
                session("s4-running", NOW.minus(Duration.ofMinutes(10)), null),
                session("s3-recent", NOW.minus(Duration.ofMinutes(50)), NOW.minus(Duration.ofMinutes(20))),
                session("s2-old", NOW.minus(Duration.ofHours(4)), NOW.minus(Duration.ofHours(3))),
                session("s1-older", NOW.minus(Duration.ofHours(8)), NOW.minus(Duration.ofHours(7))));

        @Test
        void unrestrictedFilterReturnsTheSameListInstance() {
            assertTrue(RecordingSessionFilter.ALL.isUnrestricted());
            assertSame(newestFirst, RecordingSessionFilter.ALL.apply(newestFirst));
        }

        @Test
        void windowKeepsOrderAndDropsNonMatching() {
            RecordingSessionFilter lastHour = new RecordingSessionFilter(ONE_HOUR_AGO, NOW, null, 0);

            assertEquals(List.of("s4-running", "s3-recent"), ids(lastHour.apply(newestFirst)));
        }

        @Test
        void limitKeepsTheNewestSessions() {
            RecordingSessionFilter newestTwo = RecordingSessionFilter.ALL.withLimit(2);

            assertFalse(newestTwo.isUnrestricted());
            assertEquals(List.of("s4-running", "s3-recent"), ids(newestTwo.apply(newestFirst)));
        }

        @Test
        void limitAppliesAfterTheOtherConstraints() {
            RecordingSessionFilter oneFinished = RecordingSessionFilter.ALL
                    .withStatus(RecordingStatus.FINISHED)
                    .withLimit(1);

            assertEquals(List.of("s3-recent"), ids(oneFinished.apply(newestFirst)));
        }
    }

    @Nested
    class Validation {

        @Test
        void rejectsNegativeLimit() {
            assertThrows(IllegalArgumentException.class,
                    () -> new RecordingSessionFilter(null, null, null, -1));
        }

        @Test
        void rejectsWindowWhoseStartIsAfterItsEnd() {
            assertThrows(IllegalArgumentException.class,
                    () -> new RecordingSessionFilter(NOW, ONE_HOUR_AGO, null, 0));
        }

        @Test
        void rejectsNegativeLookBack() {
            assertThrows(IllegalArgumentException.class,
                    () -> RecordingSessionFilter.activeWithinLast(Duration.ofMinutes(-1), NOW));
        }

        @Test
        void acceptsAnInstantWindow() {
            RecordingSessionFilter atInstant = new RecordingSessionFilter(NOW, NOW, null, 0);

            assertTrue(atInstant.matches(session("running", ONE_HOUR_AGO, null)));
        }
    }
}
