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

package cafe.jeffrey.profile.common.pipeline;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineSlotsTest {

    @Nested
    class Resize {

        @Test
        void reportsWhetherTheCeilingChanged() {
            PipelineSlots slots = new PipelineSlots(2);

            assertTrue(slots.resize(4));
            assertEquals(4, slots.permits());
            assertFalse(slots.resize(4), "resizing to the current ceiling changes nothing");
        }

        // Growing must hand out the difference straight away, or a queued run would keep waiting for
        // something to finish even though the ceiling was just raised for it.
        @Test
        void growingReleasesTheDifferenceImmediately() throws InterruptedException {
            PipelineSlots slots = new PipelineSlots(1);
            slots.acquire();
            assertEquals(0, slots.availablePermits());

            slots.resize(3);

            assertEquals(2, slots.availablePermits());
        }

        // Lowering the ceiling below what is in flight must not recall a permit from a running task; the
        // new ceiling takes hold as work drains, which shows up as a temporary deficit.
        @Test
        void shrinkingLeavesHeldPermitsAloneAndGoesIntoDeficit() throws InterruptedException {
            PipelineSlots slots = new PipelineSlots(4);
            slots.acquire();
            slots.acquire();
            slots.acquire();

            slots.resize(1);

            assertEquals(1, slots.permits());
            assertEquals(-2, slots.availablePermits());

            slots.release();
            slots.release();
            assertEquals(0, slots.availablePermits(), "still nothing free — the deficit absorbed both");

            slots.release();
            assertEquals(1, slots.availablePermits(), "the last release restores the new ceiling");
        }

        @Test
        void unboundedIsJustAVeryLargeCeiling() {
            PipelineSlots slots = new PipelineSlots(2);

            slots.resize(PipelineRunOptions.UNBOUNDED);

            assertEquals(PipelineRunOptions.UNBOUNDED, slots.permits());
        }
    }

    @Nested
    class Validation {

        @Test
        void refusesACeilingBelowOne() {
            assertThrows(IllegalArgumentException.class, () -> new PipelineSlots(0));
            assertThrows(IllegalArgumentException.class, () -> new PipelineSlots(2).resize(0));
        }
    }
}
