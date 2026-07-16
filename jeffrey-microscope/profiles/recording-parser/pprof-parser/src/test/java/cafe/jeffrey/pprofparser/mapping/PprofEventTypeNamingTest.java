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

import cafe.jeffrey.pprofparser.mapping.PprofEventTypeNaming.PprofEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PprofEventTypeNamingTest {

    @Test
    void namespacesAndSanitizesTheSampleType() {
        assertEquals("pprof.cpu", PprofEventTypeNaming.resolve("cpu", "nanoseconds").name());
        assertEquals("pprof.alloc_space", PprofEventTypeNaming.resolve("alloc_space", "bytes").name());
        // disallowed characters (here a space) are replaced with '_', hyphens/underscores are kept
        assertEquals("pprof.inuse_objects", PprofEventTypeNaming.resolve("inuse objects", "count").name());
    }

    @Test
    void bucketsCpuAllocationAndBlockingTypesIntoCategories() {
        assertTrue(PprofEventTypeNaming.resolve("cpu", "nanoseconds").categories().contains("CPU"));
        assertTrue(PprofEventTypeNaming.resolve("alloc_space", "bytes").categories().contains("Allocation"));
        assertTrue(PprofEventTypeNaming.resolve("contentions", "count").categories().contains("Blocking"));
        assertTrue(PprofEventTypeNaming.resolve("wall", "nanoseconds").categories().contains("Wall-Clock"));
    }

    @Test
    void fallsBackToSamplesForBlankType() {
        PprofEventType resolved = PprofEventTypeNaming.resolve("", "count");
        assertEquals("pprof.samples", resolved.name());
    }
}
