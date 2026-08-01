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

package cafe.jeffrey.profile.advisor.run;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdvisorRunTest {

    private static final AdvisorTarget TARGET = new AdvisorTarget("p1", "jdk.ExecutionSample");

    /** A clock the test advances by hand so each step gets a deterministic, non-zero duration. */
    private static final class TickingClock extends Clock {
        private Instant now;

        TickingClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            this.now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static Map<String, AdvisorStepProgress> stepsByName(AdvisorRun run) {
        return run.progress().steps().stream()
                .collect(Collectors.toMap(AdvisorStepProgress::step, Function.identity()));
    }

    @Test
    void timesEachCompletedStepAndTheActiveOne() {
        TickingClock clock = new TickingClock(Instant.parse("2026-08-01T06:00:00Z"));
        AdvisorRun run = new AdvisorRun(TARGET, clock);

        run.preparingPrompt();
        clock.advance(Duration.ofMillis(400));
        run.resolvingSource();
        clock.advance(Duration.ofMillis(200));
        run.analyzing();
        clock.advance(Duration.ofSeconds(8));

        // Mid-analyze: the two finished steps carry durations, Analyze is live, Ground is pending.
        Map<String, AdvisorStepProgress> steps = stepsByName(run);
        assertEquals(AdvisorStepProgress.COMPLETED, steps.get("PREPARING_PROMPT").status());
        assertEquals(400L, steps.get("PREPARING_PROMPT").durationMs());
        assertEquals(200L, steps.get("RESOLVING_SOURCE").durationMs());
        assertEquals(AdvisorStepProgress.IN_PROGRESS, steps.get("ANALYZING").status());
        assertEquals(8000L, steps.get("ANALYZING").elapsedMs());
        assertNull(steps.get("ANALYZING").durationMs());
        assertEquals(AdvisorStepProgress.PENDING, steps.get("GROUNDING").status());
    }

    @Test
    void completingClosesTheLastStep() {
        TickingClock clock = new TickingClock(Instant.parse("2026-08-01T06:00:00Z"));
        AdvisorRun run = new AdvisorRun(TARGET, clock);

        run.preparingPrompt();
        clock.advance(Duration.ofMillis(100));
        run.resolvingSource();
        clock.advance(Duration.ofMillis(100));
        run.analyzing();
        clock.advance(Duration.ofMillis(5000));
        run.grounding();
        clock.advance(Duration.ofMillis(500));
        run.completed();

        Map<String, AdvisorStepProgress> steps = stepsByName(run);
        assertEquals(AdvisorStatus.COMPLETED, run.progress().status());
        assertEquals(100L, steps.get("PREPARING_PROMPT").durationMs());
        assertEquals(100L, steps.get("RESOLVING_SOURCE").durationMs());
        assertEquals(5000L, steps.get("ANALYZING").durationMs());
        assertEquals(500L, steps.get("GROUNDING").durationMs());
        steps.values().forEach(step -> assertEquals(AdvisorStepProgress.COMPLETED, step.status()));
    }

    @Test
    void failingMarksTheActiveStepFailedAndKeepsFinishedDurations() {
        TickingClock clock = new TickingClock(Instant.parse("2026-08-01T06:00:00Z"));
        AdvisorRun run = new AdvisorRun(TARGET, clock);

        run.preparingPrompt();
        clock.advance(Duration.ofMillis(300));
        run.resolvingSource();
        clock.advance(Duration.ofMillis(150));
        run.analyzing();
        clock.advance(Duration.ofMillis(1000));
        run.failed("model error");

        Map<String, AdvisorStepProgress> steps = stepsByName(run);
        assertEquals(300L, steps.get("PREPARING_PROMPT").durationMs());
        assertEquals(150L, steps.get("RESOLVING_SOURCE").durationMs());
        assertEquals(AdvisorStepProgress.FAILED, steps.get("ANALYZING").status());
        assertNull(steps.get("ANALYZING").durationMs());
        assertEquals(AdvisorStepProgress.PENDING, steps.get("GROUNDING").status());
    }
}
