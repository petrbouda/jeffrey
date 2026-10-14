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

package cafe.jeffrey.hub.core.volume;

import cafe.jeffrey.shared.common.HeartbeatConstants;
import cafe.jeffrey.test.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a session is still recording, read from its heartbeat files with the hub's clock at the
 * moment of asking — the same answer every caller gets, since nothing stores it.
 */
class SessionLivenessTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @TempDir
    Path sessionDir;

    private MutableClock clock;
    private SessionLiveness liveness;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        liveness = TestVolume.liveness(clock);
    }

    private SessionLifecycle assess(Instant createdAt) {
        return liveness.assess(sessionDir, createdAt, null);
    }

    @Nested
    class CleanExit {

        @Test
        void finishesAtTheTimeTheMarkerCarries() {
            Instant exit = NOW.minusSeconds(3);
            TestVolume.heartbeat(sessionDir, NOW.minusSeconds(4));
            TestVolume.finished(sessionDir, exit);

            assertEquals(SessionLifecycle.finished(exit), assess(NOW.minusSeconds(600)));
        }

        /** By presence, so a producer clock ahead of the hub's cannot keep the session live. */
        @Test
        void isTakenEvenWhenItsTimeIsAheadOfTheHub() {
            Instant aheadOfTheHub = NOW.plusSeconds(120);
            TestVolume.finished(sessionDir, aheadOfTheHub);

            assertEquals(SessionLifecycle.finished(aheadOfTheHub), assess(NOW.minusSeconds(600)));
        }
    }

    @Nested
    class Heartbeat {

        @Test
        void aFreshHeartbeatIsRecording() {
            TestVolume.heartbeat(sessionDir, NOW.minusSeconds(4));

            assertTrue(assess(NOW.minusSeconds(600)).isRecording());
        }

        @Test
        void aStaleHeartbeatFinishesAtTheLastBeat() {
            Instant lastBeat = NOW.minus(TestVolume.HEARTBEAT_THRESHOLD).minusSeconds(1);
            TestVolume.heartbeat(sessionDir, lastBeat);

            assertEquals(SessionLifecycle.finished(lastBeat), assess(NOW.minusSeconds(600)));
        }

        /** Nothing is stored: the same session reads as finished once the clock passes the threshold. */
        @Test
        void isReadAgainOnEveryCall() {
            TestVolume.heartbeat(sessionDir, NOW);
            assertTrue(assess(NOW.minusSeconds(600)).isRecording());

            clock.advance(TestVolume.HEARTBEAT_THRESHOLD.plusSeconds(1));

            assertEquals(SessionLifecycle.finished(NOW), assess(NOW.minusSeconds(600)));
        }
    }

    @Nested
    class NewerSessionInTheSameInstance {

        /** The instance's next session started, so this one ended — at its last beat. */
        @Test
        void finishesAtTheLastHeartbeatWhateverItsAge() {
            Instant lastBeat = NOW.minusSeconds(2);
            TestVolume.heartbeat(sessionDir, lastBeat);

            assertEquals(SessionLifecycle.finished(lastBeat),
                    liveness.assess(sessionDir, NOW.minusSeconds(600), NOW.minusSeconds(1)));
        }

        @Test
        void finishesWhenTheNextOneStartedIfItNeverBeat() {
            Instant nextStart = NOW.minusSeconds(30);

            assertEquals(SessionLifecycle.finished(nextStart),
                    liveness.assess(sessionDir, NOW.minusSeconds(40), nextStart));
        }

        @Test
        void theCleanExitMarkerStillWins() {
            Instant exit = NOW.minusSeconds(50);
            TestVolume.finished(sessionDir, exit);
            TestVolume.heartbeat(sessionDir, NOW.minusSeconds(51));

            assertEquals(SessionLifecycle.finished(exit),
                    liveness.assess(sessionDir, NOW.minusSeconds(600), NOW.minusSeconds(40)));
        }
    }

    @Nested
    class NoLivenessFile {

        @Test
        void isRecordingWithinTheStartupGrace() {
            assertTrue(assess(NOW.minus(TestVolume.STARTUP_GRACE).plusSeconds(1)).isRecording());
        }

        @Test
        void finishesAtItsStartPastTheGraceAsMissingItsHeartbeat() {
            Instant createdAt = NOW.minus(TestVolume.STARTUP_GRACE).minusSeconds(1);

            SessionLifecycle lifecycle = assess(createdAt);

            assertEquals(SessionLifecycle.finishedWithoutHeartbeat(createdAt), lifecycle);
            assertTrue(lifecycle.heartbeatMissing());
        }
    }

    @Nested
    class UnreadableLivenessFile {

        /**
         * A file that is there and cannot be read says nothing about the JVM. Reading it as
         * finished would let compression and retention act on a chunk the profiler still writes.
         */
        @Test
        void staysRecordingPastTheGrace() throws IOException {
            Path heartbeatDir = Files.createDirectories(sessionDir.resolve(HeartbeatConstants.HEARTBEAT_DIR));
            Files.writeString(heartbeatDir.resolve(HeartbeatConstants.HEARTBEAT_FILE), "not-a-timestamp");

            SessionLifecycle lifecycle = assess(NOW.minus(Duration.ofHours(1)));

            assertTrue(lifecycle.isRecording());
            assertFalse(lifecycle.heartbeatMissing());
        }
    }
}
