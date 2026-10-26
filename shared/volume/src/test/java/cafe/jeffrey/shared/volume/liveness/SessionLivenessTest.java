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

package cafe.jeffrey.shared.volume.liveness;

import cafe.jeffrey.shared.common.HeartbeatConstants;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumeFixture;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.test.MutableClock;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a session is still recording, read from its heartbeat files with the reader's clock at the
 * moment of asking — the same answer every caller gets, since nothing stores it.
 */
class SessionLivenessTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    /** The session is the volume's root directory, so its liveness files lie right under it. */
    private static final VolumePath SESSION = VolumePath.root();

    @TempDir
    Path sessionDir;

    private MutableClock clock;
    private SessionLiveness liveness;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        liveness = VolumeFixture.liveness(clock, new FilesystemVolume(sessionDir));
    }

    private SessionLifecycle assess(Instant createdAt) {
        return liveness.assess(SESSION, createdAt, Optional::empty);
    }

    @Nested
    class CleanExit {

        @Test
        void finishesAtTheTimeTheMarkerCarries() {
            Instant exit = NOW.minusSeconds(3);
            VolumeWriter.heartbeat(sessionDir, NOW.minusSeconds(4));
            VolumeWriter.finished(sessionDir, exit);

            assertEquals(SessionLifecycle.finished(exit), assess(NOW.minusSeconds(600)));
        }

        /** By presence, so a producer clock ahead of the reader's cannot keep the session live. */
        @Test
        void isTakenEvenWhenItsTimeIsAheadOfTheHub() {
            Instant aheadOfTheHub = NOW.plusSeconds(120);
            VolumeWriter.finished(sessionDir, aheadOfTheHub);

            assertEquals(SessionLifecycle.finished(aheadOfTheHub), assess(NOW.minusSeconds(600)));
        }
    }

    @Nested
    class Heartbeat {

        @Test
        void aFreshHeartbeatIsRecording() {
            VolumeWriter.heartbeat(sessionDir, NOW.minusSeconds(4));

            assertTrue(assess(NOW.minusSeconds(600)).isRecording());
        }

        @Test
        void aStaleHeartbeatFinishesAtTheLastBeat() {
            Instant lastBeat = NOW.minus(VolumeFixture.HEARTBEAT_THRESHOLD).minusSeconds(1);
            VolumeWriter.heartbeat(sessionDir, lastBeat);

            assertEquals(SessionLifecycle.finished(lastBeat), assess(NOW.minusSeconds(600)));
        }

        /** Nothing is stored: the same session reads as finished once the clock passes the threshold. */
        @Test
        void isReadAgainOnEveryCall() {
            VolumeWriter.heartbeat(sessionDir, NOW);
            assertTrue(assess(NOW.minusSeconds(600)).isRecording());

            clock.advance(VolumeFixture.HEARTBEAT_THRESHOLD.plusSeconds(1));

            assertEquals(SessionLifecycle.finished(NOW), assess(NOW.minusSeconds(600)));
        }
    }

    @Nested
    class NewerSessionInTheSameInstance {

        /** The instance's next session started, so this one ended — at its last beat. */
        @Test
        void finishesAtTheLastHeartbeatWhateverItsAge() {
            Instant lastBeat = NOW.minusSeconds(2);
            VolumeWriter.heartbeat(sessionDir, lastBeat);

            assertEquals(SessionLifecycle.finished(lastBeat),
                    liveness.assess(SESSION, NOW.minusSeconds(600), () -> Optional.of(NOW.minusSeconds(1))));
        }

        @Test
        void finishesWhenTheNextOneStartedIfItNeverBeat() {
            Instant nextStart = NOW.minusSeconds(30);

            assertEquals(SessionLifecycle.finished(nextStart),
                    liveness.assess(SESSION, NOW.minusSeconds(40), () -> Optional.of(nextStart)));
        }

        @Test
        void theCleanExitMarkerStillWins() {
            Instant exit = NOW.minusSeconds(50);
            VolumeWriter.finished(sessionDir, exit);
            VolumeWriter.heartbeat(sessionDir, NOW.minusSeconds(51));

            assertEquals(SessionLifecycle.finished(exit),
                    liveness.assess(SESSION, NOW.minusSeconds(600), () -> Optional.of(NOW.minusSeconds(40))));
        }
    }

    @Nested
    class NoLivenessFile {

        @Test
        void isRecordingWithinTheStartupGrace() {
            assertTrue(assess(NOW.minus(VolumeFixture.STARTUP_GRACE).plusSeconds(1)).isRecording());
        }

        @Test
        void finishesAtItsStartPastTheGraceAsMissingItsHeartbeat() {
            Instant createdAt = NOW.minus(VolumeFixture.STARTUP_GRACE).minusSeconds(1);

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
