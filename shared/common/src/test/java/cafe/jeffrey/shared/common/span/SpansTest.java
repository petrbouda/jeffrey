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

package cafe.jeffrey.shared.common.span;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that {@link Spans} is a safe no-op when the native async-profiler agent is not attached —
 * the situation in every normal (non-self-profiling) run. The {@code one.profiler.*} classes are on
 * the test classpath, but {@code Recording} catches the missing-native-library error and reports
 * {@code UNAVAILABLE}, so {@link Spans#start()} returns {@code 0} and the {@code end} calls do nothing.
 */
class SpansTest {

    @Test
    void startReturnsZeroWhenProfilerNotRunning() {
        assertEquals(0L, Spans.start());
    }

    @Test
    void endIsNoOpForUnstartedSpan() {
        long span = Spans.start();
        assertDoesNotThrow(() -> Spans.end(span, "test.tag"));
    }

    @Test
    void endIfProfiledIsNoOpForUnstartedSpan() {
        long span = Spans.start();
        assertDoesNotThrow(() -> Spans.endIfProfiled(span, "test.tag"));
    }

    @Test
    void endToleratesNullTag() {
        long span = Spans.start();
        assertDoesNotThrow(() -> Spans.end(span, null));
    }
}
