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

package cafe.jeffrey.profile.heapdump.sanitizer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HprofRepairTest {

    @Nested
    class PatchRecordLengthValidation {

        @Test
        void rejectsNegativeOffset() {
            assertThrows(IllegalArgumentException.class,
                    () -> new HprofRepair.PatchRecordLength(-1L, 100L));
        }

        @Test
        void rejectsNegativeLength() {
            assertThrows(IllegalArgumentException.class,
                    () -> new HprofRepair.PatchRecordLength(0L, -1L));
        }

        @Test
        void rejectsLengthAboveU4Range() {
            assertThrows(IllegalArgumentException.class,
                    () -> new HprofRepair.PatchRecordLength(0L, 0x1_0000_0000L));
        }

        @Test
        void acceptsMaxU4Value() {
            HprofRepair.PatchRecordLength p = new HprofRepair.PatchRecordLength(42L, 0xFFFFFFFFL);
            assertEquals(42L, p.lengthFieldOffset());
            assertEquals(0xFFFFFFFFL, p.newLength());
        }

        @Test
        void acceptsZeroLength() {
            HprofRepair.PatchRecordLength p = new HprofRepair.PatchRecordLength(0L, 0L);
            assertEquals(0L, p.newLength());
        }
    }

    @Nested
    class TruncateFileValidation {

        @Test
        void rejectsNegativeOffset() {
            assertThrows(IllegalArgumentException.class,
                    () -> new HprofRepair.TruncateFile(-1L));
        }

        @Test
        void acceptsZeroOffset() {
            HprofRepair.TruncateFile t = new HprofRepair.TruncateFile(0L);
            assertEquals(0L, t.offset());
        }
    }

    @Nested
    class AppendEndMarkerSemantics {

        @Test
        void allInstancesEqual() {
            assertEquals(new HprofRepair.AppendEndMarker(), new HprofRepair.AppendEndMarker());
        }
    }
}
