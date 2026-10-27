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

import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import io.grpc.Deadline;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpDeadlinesTest {

    private static final String ELAPSED = "Hub test response deadline elapsed";

    /** A clock that never moves, so what is left of a deadline is exactly what it was armed with. */
    private static final Deadline.Ticker FROZEN = new Deadline.Ticker() {
        @Override
        public long nanoTime() {
            return 0;
        }
    };

    private static Deadline deadlineIn(Duration left) {
        return Deadline.after(left.toNanos(), TimeUnit.NANOSECONDS, FROZEN);
    }

    @Nested
    class RemainingWithin {

        @Test
        void answersWhatIsLeftWhenItIsShorterThanTheCap() {
            Duration left = McpDeadlines.remainingWithin(
                    deadlineIn(Duration.ofSeconds(3)), Duration.ofSeconds(5), ELAPSED);

            assertEquals(Duration.ofSeconds(3), left);
        }

        @Test
        void answersTheCapWhenMoreIsLeft() {
            Duration left = McpDeadlines.remainingWithin(
                    deadlineIn(Duration.ofSeconds(45)), Duration.ofSeconds(5), ELAPSED);

            assertEquals(Duration.ofSeconds(5), left);
        }

        @Test
        void answersTheCapWhenExactlyThatMuchIsLeft() {
            Duration left = McpDeadlines.remainingWithin(
                    deadlineIn(Duration.ofSeconds(5)), Duration.ofSeconds(5), ELAPSED);

            assertEquals(Duration.ofSeconds(5), left);
        }

        /** An elapsed deadline is the hub being too slow, reported as it would be by the hub itself. */
        @Test
        void refusesAnElapsedDeadlineAsTheHubBeingUnavailable() {
            JeffreyException refused = assertThrows(JeffreyException.class,
                    () -> McpDeadlines.remainingWithin(deadlineIn(Duration.ZERO), Duration.ofSeconds(5), ELAPSED));

            assertEquals(ErrorCode.HUB_UNAVAILABLE, refused.getCode());
            assertEquals(ELAPSED + " (grpc_status=DEADLINE_EXCEEDED)", refused.getMessage());
        }

        @Test
        void refusesADeadlineAlreadyPast() {
            JeffreyException refused = assertThrows(JeffreyException.class,
                    () -> McpDeadlines.remainingWithin(
                            deadlineIn(Duration.ofSeconds(-1)), Duration.ofSeconds(5), ELAPSED));

            assertTrue(refused.getMessage().startsWith(ELAPSED), refused.getMessage());
        }
    }
}
