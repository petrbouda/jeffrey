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
package cafe.jeffrey.profile.heapdump.parser;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildOptionsTest {

    @Nested
    class Defaults {

        @Test
        void exposesPublishedConstants() {
            BuildOptions opts = BuildOptions.defaults();
            assertEquals(BuildOptions.DEFAULT_STRING_CONTENT_THRESHOLD, opts.stringContentThreshold());
            assertEquals(BuildOptions.DEFAULT_WALK_WORKERS, opts.walkWorkers());
        }

        @Test
        void thresholdMatchesDocumentedValue() {
            assertEquals(4096, BuildOptions.DEFAULT_STRING_CONTENT_THRESHOLD);
        }

        @Test
        void walkWorkersMatchesDocumentedValue() {
            assertEquals(4, BuildOptions.DEFAULT_WALK_WORKERS);
        }
    }

    @Nested
    class WalkWorkersValidation {

        @Test
        void zeroRejected() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new BuildOptions(4096, 0));
            assertTrue(ex.getMessage().contains("walkWorkers"),
                    "exception message should name the offending field: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("0"),
                    "exception message should include the rejected value: " + ex.getMessage());
        }

        @Test
        void negativeRejected() {
            assertThrows(IllegalArgumentException.class, () -> new BuildOptions(4096, -5));
        }

        @Test
        void oneAccepted() {
            BuildOptions opts = new BuildOptions(4096, 1);
            assertEquals(1, opts.walkWorkers());
        }
    }

    @Nested
    class StringContentThreshold {

        @Test
        void unlimitedSentinelAccepted() {
            // -1 means "unlimited" per BuildOptions Javadoc.
            BuildOptions opts = new BuildOptions(-1, 4);
            assertEquals(-1, opts.stringContentThreshold());
        }

        @Test
        void zeroAccepted() {
            BuildOptions opts = new BuildOptions(0, 4);
            assertEquals(0, opts.stringContentThreshold());
        }
    }
}
