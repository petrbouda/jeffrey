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
