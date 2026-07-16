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

package cafe.jeffrey.pprofparser.mapping;

import cafe.jeffrey.pprofparser.mapping.PprofSampleUnit.BytesUnit;
import cafe.jeffrey.pprofparser.mapping.PprofSampleUnit.CountUnit;
import cafe.jeffrey.pprofparser.mapping.PprofSampleUnit.DurationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PprofSampleUnitTest {

    @Test
    void resolvesKnownUnits() {
        assertInstanceOf(CountUnit.class, PprofSampleUnit.fromUnitString("count"));
        assertInstanceOf(BytesUnit.class, PprofSampleUnit.fromUnitString("bytes"));
        assertInstanceOf(DurationUnit.class, PprofSampleUnit.fromUnitString("nanoseconds"));
    }

    @Test
    void normalizesDurationUnitsToNanoseconds() {
        DurationUnit millis = (DurationUnit) PprofSampleUnit.fromUnitString("milliseconds");
        assertEquals(5_000_000L, millis.toNanos(5));

        DurationUnit seconds = (DurationUnit) PprofSampleUnit.fromUnitString("seconds");
        assertEquals(2_000_000_000L, seconds.toNanos(2));
    }

    @Test
    void treatsUnknownUnitAsCount() {
        assertInstanceOf(CountUnit.class, PprofSampleUnit.fromUnitString("requests"));
        assertInstanceOf(CountUnit.class, PprofSampleUnit.fromUnitString(""));
    }
}
