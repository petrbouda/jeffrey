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
        void walkWorkersScaleWithAvailableCpusWithinClamp() {
            int expected = Math.clamp(
                    Runtime.getRuntime().availableProcessors(),
                    BuildOptions.MIN_DEFAULT_WALK_WORKERS,
                    BuildOptions.MAX_DEFAULT_WALK_WORKERS);
            assertEquals(expected, BuildOptions.DEFAULT_WALK_WORKERS);
            assertTrue(BuildOptions.DEFAULT_WALK_WORKERS >= BuildOptions.MIN_DEFAULT_WALK_WORKERS);
            assertTrue(BuildOptions.DEFAULT_WALK_WORKERS <= BuildOptions.MAX_DEFAULT_WALK_WORKERS);
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
