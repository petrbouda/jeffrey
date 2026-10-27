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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preset selection, explicit family overrides and startup validation of the tool surface.
 */
class ExternalMcpPropertiesTest {

    private static final String JFR = "jfr";
    private static final String HEAP = "heap";

    private static ExternalMcpProperties withFamilies(Set<String> families) {
        return McpTestProperties.of(true, true, true, families);
    }

    @Test
    void anEmptyFilterAdvertisesEveryFamily() {
        ExternalMcpProperties properties = withFamilies(Set.of());

        assertTrue(properties.advertises(JFR));
        assertTrue(properties.advertises(HEAP));
    }

    /**
     * An unset property binds to null rather than to an empty set, and a server that then refused
     * every family would answer an empty tool list to an installation that configured nothing.
     */
    @Test
    void treatsAnUnsetFilterAsEveryFamily() {
        ExternalMcpProperties properties = withFamilies(null);

        assertEquals(Set.of(), properties.families());
        assertTrue(properties.advertises(JFR));
    }

    @Test
    void advertisesOnlyTheNamedFamiliesWhenTheFilterIsSet() {
        ExternalMcpProperties properties = withFamilies(Set.of(JFR));

        assertTrue(properties.advertises(JFR));
        assertFalse(properties.advertises(HEAP));
    }

    /**
     * The record is read on every request that lists tools, so it must not be able to change under
     * one: the set it keeps is its own copy.
     */
    @Test
    void keepsItsOwnCopyOfTheFilter() {
        Set<String> mutable = new LinkedHashSet<>(Set.of(JFR));
        ExternalMcpProperties properties = withFamilies(mutable);

        mutable.add(HEAP);

        assertFalse(properties.advertises(HEAP));
        assertThrows(UnsupportedOperationException.class, () -> properties.families().add(HEAP));
    }
    @Test
    void rejectsUnknownOrIncorrectlyCasedFamiliesAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> withFamilies(Set.of("heep")));
        assertThrows(IllegalArgumentException.class, () -> withFamilies(Set.of("JFR")));
    }

    @Test
    void rejectsUnknownOrIncorrectlyCasedPresetsEvenWhenFamiliesOverride() {
        assertThrows(IllegalArgumentException.class,
                () -> McpTestProperties.of(true, true, true, Set.of("heap"), "heep"));
        assertThrows(IllegalArgumentException.class,
                () -> McpTestProperties.of(true, true, true, Set.of(), "JFR"));
    }

    @Test
    void emptyFamiliesSelectThePresetAndExplicitFamiliesOverrideIt() {
        ExternalMcpProperties heap = McpTestProperties.of(true, true, true, Set.of(), "heap");
        assertTrue(heap.advertises("profiles"));
        assertTrue(heap.advertises("recordings"));
        assertTrue(heap.advertises("heap"));
        assertFalse(heap.advertises("jfr"));

        ExternalMcpProperties override = McpTestProperties.of(
                true, true, true, Set.of("jfr"), "heap");
        assertTrue(override.advertises("jfr"));
        assertFalse(override.advertises("heap"));
    }

    /**
     * A writer's long work is followed through operations_status and stopped through
     * operations_cancel; an explicit list that serves a writer without them hands out operationIds
     * nobody can poll.
     */
    @Nested
    class OperationsRule {

        @ParameterizedTest
        @ValueSource(strings = {"recordings", "heap", "hubs", "ide", "jvm"})
        void refusesAWriterFamilyWithoutOperations(String writer) {
            IllegalArgumentException failure =
                    assertThrows(IllegalArgumentException.class, () -> withFamilies(Set.of(writer)));

            assertTrue(failure.getMessage().contains("operations"), failure.getMessage());
            assertTrue(failure.getMessage().contains(writer), failure.getMessage());
        }

        @Test
        void acceptsAWriterFamilyThatKeepsOperations() {
            ExternalMcpProperties properties = withFamilies(Set.of(HEAP, "operations"));

            assertTrue(properties.advertises(HEAP));
            assertTrue(properties.advertises("operations"));
        }

        @Test
        void acceptsReadOnlyFamiliesWithoutOperations() {
            ExternalMcpProperties properties = withFamilies(Set.of(JFR, "flamegraph"));

            assertFalse(properties.advertises("operations"));
        }
    }

    /**
     * A hub download is a recording nobody can analyse unless recordings_analyzeRecording is served
     * too, so an explicit list with hubs has to keep recordings.
     */
    @Nested
    class RecordingsBesideHubsRule {

        @Test
        void refusesHubsWithoutRecordings() {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> withFamilies(Set.of("hubs", "operations")));

            assertTrue(failure.getMessage().contains("recordings"), failure.getMessage());
            assertTrue(failure.getMessage().contains("hubs"), failure.getMessage());
        }

        @Test
        void acceptsHubsWithRecordings() {
            ExternalMcpProperties properties = withFamilies(Set.of("hubs", "recordings", "operations"));

            assertTrue(properties.advertises("hubs"));
            assertTrue(properties.advertises("recordings"));
        }
    }

    /**
     * The jfr preset serves everything analyze-jfr routes to, so a reader who picked it is never told
     * a tool the skill names does not exist.
     */
    @Test
    void theJfrPresetServesEveryFamilyAnalyzeJfrRoutesTo() {
        ExternalMcpProperties jfr = McpTestProperties.of(true, true, true, Set.of(), JFR);

        for (String family : Set.of("profiles", "recordings", "jfr", "flamegraph", "jvm", "compare",
                "operations", "traces", "http", "jdbc", "grpc", "methodtracing", "io", "blocking",
                "timeline", "memory")) {
            assertTrue(jfr.advertises(family), family);
        }
        assertFalse(jfr.advertises(HEAP));
        assertFalse(jfr.advertises("hubs"));
        assertFalse(jfr.advertises("ide"));
    }

    @Test
    void leavesTheHeapPresetAsItWas() {
        ExternalMcpProperties heap = McpTestProperties.of(true, true, true, Set.of(), HEAP);

        for (String family : ExternalMcpProperties.knownFamilies()) {
            assertEquals(Set.of("profiles", "recordings", HEAP, "operations").contains(family),
                    heap.advertises(family), family);
        }
    }

    @Nested
    class Access {

        @Test
        void trustsNoForwardedHeadersAndRequiresNoTokenByDefault() {
            ExternalMcpProperties properties = withFamilies(Set.of());

            assertFalse(properties.trustForwardedHeaders());
            assertEquals("", properties.token());
            assertFalse(properties.tokenRequired());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void treatsABlankTokenAsNone(String token) {
            ExternalMcpProperties properties =
                    new ExternalMcpProperties(true, true, true, Set.of(), "all", false, token);

            assertFalse(properties.tokenRequired());
        }

        @Test
        void treatsAnUnsetTokenAsNone() {
            assertFalse(new ExternalMcpProperties(true, true, true, Set.of(), "all", false, null).tokenRequired());
        }

        /**
         * A record prints every component, and the properties bean ends up in logs and debugger
         * views; the secret must not.
         */
        @Test
        void keepsTheTokenOutOfItsStringForm() {
            ExternalMcpProperties properties =
                    new ExternalMcpProperties(true, true, true, Set.of(), "all", true, "s3cret-token");

            assertTrue(properties.tokenRequired());
            assertTrue(properties.trustForwardedHeaders());
            assertFalse(properties.toString().contains("s3cret-token"), properties.toString());
        }
    }
}
