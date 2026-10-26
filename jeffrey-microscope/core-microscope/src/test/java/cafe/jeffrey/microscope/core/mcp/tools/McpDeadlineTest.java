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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpDeadlineTest {

    private static final String ELAPSED = "Remote test response deadline elapsed";

    /** A clock that never moves, so what is left of a deadline is exactly what it was armed with. */
    private static final LongSupplier FROZEN = () -> 0L;

    private static McpDeadline deadlineIn(Duration left) {
        return McpDeadline.after(left, FROZEN);
    }

    @Nested
    class RemainingWithin {

        @Test
        void answersWhatIsLeftWhenItIsShorterThanTheCap() {
            Duration left = deadlineIn(Duration.ofSeconds(3)).remainingWithin(Duration.ofSeconds(5), ELAPSED);

            assertEquals(Duration.ofSeconds(3), left);
        }

        @Test
        void answersTheCapWhenMoreIsLeft() {
            Duration left = deadlineIn(Duration.ofSeconds(45)).remainingWithin(Duration.ofSeconds(5), ELAPSED);

            assertEquals(Duration.ofSeconds(5), left);
        }

        @Test
        void answersTheCapWhenExactlyThatMuchIsLeft() {
            Duration left = deadlineIn(Duration.ofSeconds(5)).remainingWithin(Duration.ofSeconds(5), ELAPSED);

            assertEquals(Duration.ofSeconds(5), left);
        }

        /** An elapsed deadline is the remote being too slow, reported as the remote would report it. */
        @Test
        void refusesAnElapsedDeadlineAsTheRemoteBeingUnavailable() {
            JeffreyException refused = assertThrows(JeffreyException.class,
                    () -> deadlineIn(Duration.ZERO).remainingWithin(Duration.ofSeconds(5), ELAPSED));

            assertEquals(ErrorCode.REMOTE_UNAVAILABLE, refused.getCode());
            assertEquals(ELAPSED, refused.getMessage());
        }

        @Test
        void refusesADeadlineAlreadyPast() {
            JeffreyException refused = assertThrows(JeffreyException.class,
                    () -> deadlineIn(Duration.ofSeconds(-1)).remainingWithin(Duration.ofSeconds(5), ELAPSED));

            assertTrue(refused.getMessage().startsWith(ELAPSED), refused.getMessage());
        }
    }
}
