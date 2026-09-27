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


package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NextStepsTest {

    private static final AdvertisedFamilies TIMELINE_ONLY =
            new AdvertisedFamilies(Set.of(AdvertisedFamilies.TIMELINE));

    private static final McpNextTool HOT_WINDOWS = McpNextTool.call("timeline_hotWindows")
            .with("profileId", "p-1").why("says when the load peaked");
    private static final McpNextTool HISTOGRAM = McpNextTool.call("heap_getClassHistogram")
            .with("profileId", "p-1").why("says which classes hold the heap");
    private static final String RECORD_ALLOCATION = "record with jdk.ObjectAllocationSample enabled to see allocation";

    @Nested
    class FamilyGate {

        @Test
        void keepsACallToAnAdvertisedFamily() {
            McpFollowUp followUp = NextSteps.builder(TIMELINE_ONLY).next(HOT_WINDOWS).followUp();

            assertEquals(List.of(HOT_WINDOWS), followUp.nextTools());
        }

        /** A call to a withheld family is a tool this installation does not serve, so it is left out. */
        @Test
        void dropsACallToAWithheldFamily() {
            McpFollowUp followUp = NextSteps.builder(TIMELINE_ONLY).next(HISTOGRAM).next(HOT_WINDOWS).followUp();

            assertEquals(List.of(HOT_WINDOWS), followUp.nextTools());
        }

        @Test
        void gatesAConditionalCallTheSameWay() {
            McpFollowUp followUp = NextSteps.builder(TIMELINE_ONLY)
                    .nextWhen(true, HISTOGRAM)
                    .nextWhen(false, HOT_WINDOWS)
                    .followUp();

            assertTrue(followUp.nextTools().isEmpty(), followUp.toString());
        }

        @Test
        void keepsAConditionalCallThatOccurred() {
            McpFollowUp followUp = NextSteps.builder(TIMELINE_ONLY).nextWhen(true, HOT_WINDOWS).followUp();

            assertEquals(List.of(HOT_WINDOWS), followUp.nextTools());
        }

        /**
         * A call built from a value that exists only when the thing happened is built only then, so the
         * guard and the construction cannot drift apart into a null argument.
         */
        @Test
        void buildsAConditionalCallOnlyWhenItOccurred() {
            McpFollowUp followUp = NextSteps.builder(TIMELINE_ONLY)
                    .nextWhen(false, () -> {
                        throw new AssertionError("built although it did not occur");
                    })
                    .nextWhen(true, () -> HOT_WINDOWS)
                    .followUp();

            assertEquals(List.of(HOT_WINDOWS), followUp.nextTools());
        }
    }

    @Nested
    class Guidance {

        @Test
        void passesAdviceThroughInOrder() {
            McpFollowUp followUp = NextSteps.builder(TIMELINE_ONLY)
                    .guidance(RECORD_ALLOCATION)
                    .guidance("compare against a run without the agent attached")
                    .followUp();

            assertEquals(List.of(RECORD_ALLOCATION, "compare against a run without the agent attached"),
                    followUp.guidance());
        }

        /** A hint to a withheld family comes back empty, and an empty line is left out. */
        @Test
        void leavesOutAnEmptyLine() {
            McpFollowUp followUp = NextSteps.builder(TIMELINE_ONLY)
                    .guidance(TIMELINE_ONLY.hint(AdvertisedFamilies.HEAP, "a heap dump would show the holders"))
                    .followUp();

            assertTrue(followUp.guidance().isEmpty(), followUp.toString());
        }

        @Test
        void addsConditionalAdviceOnlyWhenItOccurred() {
            McpFollowUp followUp = NextSteps.builder(TIMELINE_ONLY)
                    .guidanceWhen(false, "not this")
                    .guidanceWhen(true, RECORD_ALLOCATION)
                    .followUp();

            assertEquals(List.of(RECORD_ALLOCATION), followUp.guidance());
        }

        @Test
        void buildsConditionalAdviceOnlyWhenItOccurred() {
            McpFollowUp followUp = NextSteps.builder(TIMELINE_ONLY)
                    .guidanceWhen(false, () -> {
                        throw new AssertionError("built although it did not occur");
                    })
                    .guidanceWhen(true, () -> RECORD_ALLOCATION)
                    .followUp();

            assertEquals(List.of(RECORD_ALLOCATION), followUp.guidance());
        }
    }

    @Nested
    class FollowUp {

        @Test
        void anEmptyBuilderGivesAnEmptyFollowUp() {
            McpFollowUp followUp = NextSteps.builder(TIMELINE_ONLY).followUp();

            assertTrue(followUp.nextTools().isEmpty());
            assertTrue(followUp.guidance().isEmpty());
        }

        @Test
        void conformsToTheGeneratedSchema() {
            McpFollowUp followUp = NextSteps.builder(TIMELINE_ONLY)
                    .next(HOT_WINDOWS).guidance(RECORD_ALLOCATION).followUp();

            McpSchemaConformance.assertConforms(Json.toTree(followUp), McpSchemaGenerator.schemaOf(McpFollowUp.class));
        }

    }

    /**
     * The prose list is gone with the last family that used it: the follow-up is the one way an answer
     * says where to go next.
     */
    @Nested
    class Modes {

        private static final Set<String> FOLLOW_UP_METHODS =
                Set.of("next", "nextWhen", "guidance", "guidanceWhen", "guidanceFor", "followUp");

        @Test
        void theFollowUpBuilderOffersNoProseMethod() {
            assertEquals(FOLLOW_UP_METHODS, declaredMethods(NextSteps.Builder.class));
        }

        @Test
        void noProseBuilderIsLeft() {
            assertEquals(Set.of("Builder"), Stream.of(NextSteps.class.getDeclaredClasses())
                    .map(Class::getSimpleName)
                    .collect(Collectors.toSet()));
        }

        /** A guidance line that names another family's tool is kept only where that family is served. */
        @Test
        void guidanceForAFamilyIsKeptOnlyWhereTheFamilyIsServed() {
            McpFollowUp served = NextSteps.builder(AdvertisedFamiliesFixture.EVERY_FAMILY)
                    .guidanceFor(AdvertisedFamilies.FLAMEGRAPH, "see flamegraph_list").followUp();
            McpFollowUp withheld = NextSteps.builder(new AdvertisedFamilies(Set.of(AdvertisedFamilies.JVM)))
                    .guidanceFor(AdvertisedFamilies.FLAMEGRAPH, "see flamegraph_list").followUp();

            assertEquals(List.of("see flamegraph_list"), served.guidance());
            assertEquals(List.of(), withheld.guidance());
        }

        private static Set<String> declaredMethods(Class<?> type) {
            return Stream.of(type.getDeclaredMethods())
                    .filter(method -> !method.isSynthetic() && !Modifier.isPrivate(method.getModifiers()))
                    .map(Method::getName)
                    .collect(Collectors.toSet());
        }
    }
}
