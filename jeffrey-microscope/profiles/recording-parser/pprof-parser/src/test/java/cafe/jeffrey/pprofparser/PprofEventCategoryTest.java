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

package cafe.jeffrey.pprofparser;

import cafe.jeffrey.profile.common.pprof.PprofEventCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PprofEventCategoryTest {

    @Test
    void mapsPprofCpuDimensionsToExecution() {
        // pprof's CPU event types differ from JFR's jdk.ExecutionSample — both resolve to EXECUTION
        assertEquals(PprofEventCategory.EXECUTION, PprofEventCategory.resolve("pprof.cpu"));
        assertEquals(PprofEventCategory.EXECUTION, PprofEventCategory.resolve("pprof.samples"));
    }

    @Test
    void mapsAllocationBlockingAndWallDimensions() {
        assertEquals(PprofEventCategory.ALLOCATION, PprofEventCategory.resolve("pprof.alloc_space"));
        assertEquals(PprofEventCategory.ALLOCATION, PprofEventCategory.resolve("pprof.inuse_objects"));
        assertEquals(PprofEventCategory.BLOCKING, PprofEventCategory.resolve("pprof.contentions"));
        assertEquals(PprofEventCategory.BLOCKING, PprofEventCategory.resolve("pprof.delay"));
        assertEquals(PprofEventCategory.WALL, PprofEventCategory.resolve("pprof.wall"));
    }

    @Test
    void unknownDimensionsAndNonPprofCodesAreOther() {
        assertEquals(PprofEventCategory.OTHER, PprofEventCategory.resolve("pprof.goroutine"));
        assertEquals(PprofEventCategory.OTHER, PprofEventCategory.resolve("jdk.ExecutionSample"));
        assertEquals(PprofEventCategory.OTHER, PprofEventCategory.resolve(null));
    }
}
