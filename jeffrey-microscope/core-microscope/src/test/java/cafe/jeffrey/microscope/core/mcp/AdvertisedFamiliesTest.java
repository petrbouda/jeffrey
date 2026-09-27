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

package cafe.jeffrey.microscope.core.mcp;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvertisedFamiliesTest {

    private static final String HINT = "timeline_hotWindows says when.";

    @Nested
    class FromProperties {

        @Test
        void theDefaultAdvertisesEveryKnownFamily() {
            AdvertisedFamilies advertised = AdvertisedFamilies.of(
                    McpTestProperties.of(true, true, true, Set.of()));

            assertEquals(ExternalMcpProperties.knownFamilies(), advertised.families());
        }

        @Test
        void aPresetAdvertisesOnlyItsFamilies() {
            AdvertisedFamilies advertised = AdvertisedFamilies.of(
                    McpTestProperties.of(true, true, true, Set.of(), "heap"));

            assertEquals(Set.of("profiles", "recordings", "heap", "operations"), advertised.families());
        }

        @Test
        void anExplicitListOverridesThePreset() {
            AdvertisedFamilies advertised = AdvertisedFamilies.of(
                    McpTestProperties.of(true, true, true, Set.of("profiles", "flamegraph"), "heap"));

            assertEquals(Set.of("profiles", "flamegraph"), advertised.families());
        }

        /** The hub and IDE switches withhold a family a preset or list selected, as the assembler does. */
        @Test
        void theHubAndIdeSwitchesWithholdTheirFamilies() {
            AdvertisedFamilies advertised = AdvertisedFamilies.of(
                    McpTestProperties.of(true, false, false, Set.of()));

            assertFalse(advertised.has(AdvertisedFamilies.HUBS));
            assertFalse(advertised.has(AdvertisedFamilies.IDE));
            assertTrue(advertised.has(AdvertisedFamilies.HEAP));
        }
    }

    @Nested
    class Hint {

        @Test
        void keepsTheTextOfAnAdvertisedFamily() {
            AdvertisedFamilies advertised = new AdvertisedFamilies(Set.of(AdvertisedFamilies.TIMELINE));

            assertEquals(HINT, advertised.hint(AdvertisedFamilies.TIMELINE, HINT));
        }

        @Test
        void answersWithTheFallbackForAHiddenFamilyWhenOneIsGiven() {
            AdvertisedFamilies advertised = new AdvertisedFamilies(Set.of(AdvertisedFamilies.FLAMEGRAPH));

            assertEquals("fallback", advertised.hintOr(AdvertisedFamilies.TIMELINE, HINT, "fallback"));
            assertEquals(HINT, advertised.hintOr(AdvertisedFamilies.FLAMEGRAPH, HINT, "fallback"));
        }

        @Test
        void dropsTheTextOfAHiddenFamily() {
            AdvertisedFamilies advertised = new AdvertisedFamilies(Set.of(AdvertisedFamilies.FLAMEGRAPH));

            assertEquals("", advertised.hint(AdvertisedFamilies.TIMELINE, HINT));
        }
    }

    /** A tool belongs to the family its name starts with, the prefix before the first underscore. */
    @Nested
    class ServesTool {

        @Test
        void servesAToolOfAnAdvertisedFamily() {
            AdvertisedFamilies advertised = new AdvertisedFamilies(Set.of(AdvertisedFamilies.TIMELINE));

            assertTrue(advertised.servesTool("timeline_hotWindows"));
        }

        @Test
        void withholdsAToolOfAHiddenFamily() {
            AdvertisedFamilies advertised = new AdvertisedFamilies(Set.of(AdvertisedFamilies.TIMELINE));

            assertFalse(advertised.servesTool("heap_getClassHistogram"));
        }

        /** The same test the hint makes: a tool is served exactly when a hint to its family is kept. */
        @Test
        void agreesWithTheHintForEveryKnownFamily() {
            AdvertisedFamilies advertised = new AdvertisedFamilies(Set.of(AdvertisedFamilies.JVM, AdvertisedFamilies.HEAP));

            for (String family : ExternalMcpProperties.knownFamilies()) {
                assertEquals(!advertised.hint(family, HINT).isEmpty(), advertised.servesTool(family + "_any"), family);
            }
        }
    }

    @Test
    void rejectsAMissingFamilySet() {
        assertThrows(IllegalArgumentException.class, () -> new AdvertisedFamilies(null));
    }
}
