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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.provider.profile.api.TraceContextCategory;
import cafe.jeffrey.shared.common.model.EventTypeName;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pact between the thread timeline and the trace context lane, which both {@code ThreadState}
 * and {@code TraceContextCategory} describe in prose and neither could enforce.
 * <p>
 * Both classes map JFR event types to "what a thread was doing", for two different pictures: a lane
 * on the thread timeline, and an explanation of where a span's time went. Their javadocs warn that
 * the two drift if either is edited alone, and until now nothing checked.
 * <p>
 * The invariant is deliberately <b>containment, not equality</b>. Every wait a thread lane can draw
 * must be a wait the trace lane can explain, or a reader who sees a band on one screen finds nothing
 * accounting for it on the other. The reverse does not hold and should not: the context side also
 * carries an {@code fsync}, a ZGC allocation stall, a deoptimisation and a virtual-thread pin, none
 * of which the timeline draws as a lane of its own.
 */
@DisplayName("ThreadState / TraceContextCategory pact")
class ThreadStateContextPactTest {

    /**
     * The lifespan states, excluded because they are not waits at all. They are reconstructed from
     * start/end pairs to bound a lane, and there is no "time spent starting" for a trace to explain.
     */
    private static final Set<ThreadState> LIFESPAN = Set.of(ThreadState.STARTED, ThreadState.ENDED);

    @Test
    @DisplayName("every wait the thread timeline draws is one the trace lane can explain")
    void everyTimelineWaitHasAContextCategory() {
        Set<String> unexplained = new TreeSet<>();
        for (ThreadState state : ThreadState.values()) {
            if (LIFESPAN.contains(state)) {
                continue;
            }
            String eventType = state.eventType().code();
            if (TraceContextCategory.fromEventType(eventType).isEmpty()) {
                unexplained.add(eventType);
            }
        }

        assertTrue(unexplained.isEmpty(),
                "these thread-timeline waits have no TraceContextCategory, so a band on the threads "
                        + "screen has nothing accounting for it on a trace: " + unexplained);
    }

    @Test
    @DisplayName("the context side may carry waits the timeline has no lane for")
    void containmentIsNotEquality() {
        // Pins the direction of the invariant, so a future edit that "fixes" the asymmetry by
        // deleting categories fails here rather than silently narrowing what a trace can explain.
        Set<String> timelineWaits = new TreeSet<>();
        for (ThreadState state : ThreadState.values()) {
            if (!LIFESPAN.contains(state)) {
                timelineWaits.add(state.eventType().code());
            }
        }

        // Every derivation, not just the one this test happened to be written against: a category
        // recovered by differencing a counter explains a wait exactly as well as one read straight
        // off an event, and narrowing to a single derivation would let a new one drift in unchecked
        // -- which is the whole failure mode this test exists to catch.
        Set<String> contextWaits = new TreeSet<>();
        for (TraceContextCategory.Derivation derivation : TraceContextCategory.Derivation.values()) {
            contextWaits.addAll(
                    TraceContextCategory.eventTypesOf(TraceContextCategory.Scope.THREAD, derivation));
        }

        assertTrue(contextWaits.containsAll(timelineWaits), "containment must hold");
        assertTrue(contextWaits.contains(EventTypeName.FILE_FORCE),
                "fsync is context without being a lane");
        assertTrue(contextWaits.contains(EventTypeName.VIRTUAL_THREAD_PINNED),
                "a pin is context without being a lane");
    }
}
