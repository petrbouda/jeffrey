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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.flamegraph.diff.DbBasedDiffgraphGenerator;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.shared.common.model.Type;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * {@code useWeight} is nullable and means "the caller did not say". A single-profile flamegraph has
 * always answered that from the event type; the differential path had no answer at all, so the flag
 * reached a primitive and threw a NullPointerException at the caller — {@code compare_movements} and
 * {@code compare_flamegraph} both failed outright whenever the argument was omitted.
 * <p>
 * Defaulting it to false would have been the worse repair: "the caller did not say" would then mean
 * one thing for a flamegraph and another for a comparison of the very same event type. These tests
 * pin the diff path to the answer the single-profile path already gives.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiffFlamegraphWeightDefaultTest {

    @Mock
    DbBasedDiffgraphGenerator generator;

    private DiffFlamegraphManagerImpl manager() {
        return new DiffFlamegraphManagerImpl(null, null, null, null, generator, null);
    }

    private GraphParameters captureAdjusted(Type eventType, Boolean useWeight) {
        manager().generate(GraphParameters.builder()
                .withEventType(eventType)
                .withUseWeight(useWeight)
                .build());

        ArgumentCaptor<GraphParameters> captor = ArgumentCaptor.forClass(GraphParameters.class);
        verify(generator).generate(captor.capture());
        return captor.getValue();
    }

    @Nested
    class WhenTheCallerDidNotSay {

        @Test
        void weighsAnAllocationComparison() {
            assertTrue(captureAdjusted(Type.OBJECT_ALLOCATION_SAMPLE, null).useWeight());
        }

        @Test
        void weighsABlockingComparison() {
            assertTrue(captureAdjusted(Type.JAVA_MONITOR_ENTER, null).useWeight());
        }

        /**
         * Execution samples have no weight worth summing, so they stay counted - the same answer the
         * single-profile path gives.
         */
        @Test
        void countsAnExecutionSampleComparison() {
            assertFalse(captureAdjusted(Type.EXECUTION_SAMPLE, null).useWeight());
        }

        /**
         * The regression itself. Whatever the answer, it must be set by the time it reaches the
         * generator: the formatter behind it takes a primitive, so a null arrived at the caller as a
         * NullPointerException rather than as a result.
         */
        @Test
        void neverLeavesItUnsetForTheGenerator() {
            assertNotNull(captureAdjusted(Type.EXECUTION_SAMPLE, null).useWeight());
        }
    }

    @Nested
    class WhenTheCallerSaid {

        @Test
        void keepsAnExplicitTrue() {
            assertTrue(captureAdjusted(Type.EXECUTION_SAMPLE, true).useWeight());
        }

        /**
         * An explicit false on an allocation event has to survive, or the caller cannot ask for the
         * call-count view of a weighted event type at all.
         */
        @Test
        void keepsAnExplicitFalse() {
            assertFalse(captureAdjusted(Type.OBJECT_ALLOCATION_SAMPLE, false).useWeight());
        }
    }
}
