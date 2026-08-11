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

package cafe.jeffrey.hub.core.scheduler.job.descriptor;

import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectInstanceSessionCleanerJobDescriptorTest {

    @Test
    void paramsRoundTripThroughFactory() {
        ProjectInstanceSessionCleanerJobDescriptor descriptor =
                new ProjectInstanceSessionCleanerJobDescriptor(7, ChronoUnit.DAYS, 10);

        assertEquals(
                Map.of("duration", "7", "time-unit", "Days", "max-sessions", "10"),
                descriptor.params());
        assertEquals(descriptor, ProjectInstanceSessionCleanerJobDescriptor.of(descriptor.params()));
    }

    @Test
    void rejectsNonPositiveMaxSessions() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectInstanceSessionCleanerJobDescriptor(7, ChronoUnit.DAYS, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectInstanceSessionCleanerJobDescriptor(7, ChronoUnit.DAYS, -1));
    }
}
