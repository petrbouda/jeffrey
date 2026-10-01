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

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpInstructionsTest {

    private static final List<String> WRITERS = List.of(
            "recordings_analyzeFile", "recordings_analyzeRecording", "recordings_delete", "heap_prepare",
            "heap_oql", "hubs_download", "hubs_fetchFile", "jvm_autoAnalysis", "operations_cancel",
            "ide_link", "ide_open");

    private static String text(Set<String> families) {
        return McpInstructions.text(new AdvertisedFamilies(families));
    }

    @Nested
    class EveryFamily {

        private final String text = text(ExternalMcpProperties.knownFamilies());

        @Test
        void describesEveryFamily() {
            for (String family : ExternalMcpProperties.knownFamilies()) {
                assertTrue(text.contains(family + "_"), family + " is missing: " + text);
            }
        }

        @Test
        void namesAllElevenWriters() {
            assertTrue(text.contains("Eleven tools are not read-only"), text);
            for (String writer : WRITERS) {
                assertTrue(text.contains(writer), writer + " is missing: " + text);
            }
            assertTrue(text.contains("wait up to 45 s, then answer with an operationId"), text);
        }

        /**
         * One text for every client, so it names both paths: the operationId a client without the
         * tasks extension polls, and the task one that declared it is handed after the short wait.
         */
        @Test
        void describesTheTaskPathBesideTheOperationId() {
            assertTrue(text.contains("A client that declared the MCP tasks extension gets a standard task "
                    + "after about 5 s instead"), text);
            assertTrue(text.contains("whose taskId is that operationId"), text);
            assertTrue(text.contains("follow it with tasks/get and stop it with tasks/cancel"), text);
        }

        /**
         * heap_prepare never waits, on either path, and which handle comes back is the client's
         * declaration, not the server's choice: the note has to say both.
         */
        @Test
        void saysHeapPrepareHandsBackAtOnceTheHandleTheClientsDeclarationPicks() {
            assertTrue(text.contains("heap_prepare does not wait: a client that declared the tasks extension "
                    + "gets its task at once, any other client its operationId"), text);
            assertFalse(text.contains("its operationId, or its task, at once"), text);
            assertFalse(text.contains("(heap_prepare answers with one at once)"), text);
        }

        @Test
        void letsARunningImportOrTransferBeFollowedAsATask() {
            assertTrue(text.contains("answered with a running operationId or a task is followed, not "
                    + "called again"), text);
            assertTrue(text.contains("joined by calling hubs_download with the arguments its followUp names, "
                    + "or by following its task"), text);
            assertTrue(text.contains("a window named by its window argument"), text);
            assertFalse(text.contains("startTime"), text);
        }

        /** Call order moved out of the tool descriptions; it has to arrive somewhere. */
        @Test
        void carriesTheCallOrderOfEveryFamily() {
            for (String rule : List.of(
                    "flamegraph_list gives the eventType values flamegraph_export accepts",
                    "compare_list comes before any other compare_ tool",
                    "traces_overview first",
                    "jvm_sections comes before the other jvm_ tools",
                    "jfr_describeEventType the fields inside one",
                    "heap_status is polled after it",
                    "starts at hubs_sessions",
                    "ide_resolve comes before a finding names a file",
                    "http_overview, jdbc_overview, grpc_overview, methodtracing_overview, io_overview")) {
                assertTrue(text.contains(rule), rule + " is missing: " + text);
            }
        }

        /**
         * The same ten files are served two ways, and {@code skills/*} answer every client, declared or
         * not; the text names both paths and never implies the skills are gated on a declaration.
         */
        @Test
        void offersTheGuidanceAsPromptsAndAsSkills() {
            assertTrue(text.contains("Fuller guidance is served as prompts and as skills."), text);
            assertTrue(text.contains("prompts/list names ten, one per workflow"), text);
            assertTrue(text.contains("skills/list (the io.modelcontextprotocol/skills extension) serves "
                    + "the same ten to any client that asks"), text);
            assertTrue(text.contains("their files are read with resources/read"), text);
            assertFalse(text.contains("declared the MCP skills extension"), text);
        }

        /** The two per-tool hints are in every tool's _meta; the text says what they mean, once. */
        @Test
        void explainsTheCostAndRequiresHints() {
            assertTrue(text.contains("jeffrey/cost is CHEAP, MODERATE, EXPENSIVE, or SLOW for one that may "
                    + "finish its work in the background"), text);
            assertTrue(text.contains("jeffrey/requires, when present, lists what must already be in place, "
                    + "such as TRACES or HEAP_DUMP_INDEXED."), text);
        }

        /**
         * The conventions every answer follows are what an agent reads on every call; they are stated
         * once, here, rather than rediscovered from a hundred schemas.
         */
        @Test
        void statesTheResultConventionsAnAgentMustKnow() {
            for (String convention : List.of(
                    "structuredContent is a typed record, valid against its outputSchema",
                    "Times are UTC epoch milliseconds (…EpochMs)",
                    "durations carry their unit (…Ms, …Nanos)",
                    "Enum values are upper case, in answers and arguments alike",
                    "answers with a status such as NOT_RECORDED and a reason, not an error",
                    "an unknown id is an error naming it",
                    "pass nextCursor back as cursor with the same arguments",
                    "followUp.nextTools are the next calls",
                    "followUp.guidance is advice that is not a call",
                    "uiLink opens the same thing in Microscope for the user: give it to them")) {
                assertTrue(text.contains(convention), convention + " is missing: " + text);
            }
        }

        /** Truncation is declared by the answer itself; the old trimmed-tree marker is not what agents see. */
        @Test
        void saysHowACutIsDeclared() {
            assertTrue(text.contains("output is capped at 120,000 characters and always says when it cut"), text);
            assertTrue(text.contains("a structured answer in its own fields"), text);
            assertFalse(text.contains("_truncated"), text);
        }

        @Test
        void pointsAtTheArtifactsAndTheHeapIndex() {
            assertTrue(text.contains("hubs_fetchFile on the file"), text);
            assertTrue(text.contains("heap_prepare builds it"), text);
        }
    }

    @Nested
    class TrimmedFamilies {

        /** A reader told about timeline_ or traces_ under this list would call tools that do not exist. */
        @Test
        void namesNoFamilyThatIsNotAdvertised() {
            String text = text(Set.of("profiles", "jfr", "flamegraph"));

            for (String hidden : List.of("timeline_", "traces_", "heap_", "hubs_", "recordings_", "jvm_",
                    "compare_", "ide_", "operations_", "memory_")) {
                assertFalse(text.contains(hidden), hidden + " is named although not advertised: " + text);
            }
            assertTrue(text.contains("flamegraph_"), text);
            assertTrue(text.contains("jfr_"), text);
        }

        /**
         * Every tool carries a cost, so the cost sentence stays; but neither family here has a SLOW tool
         * or a tool with a requirement, so neither is offered as an example.
         */
        @Test
        void explainsOnlyTheHintsTheAdvertisedToolsCarry() {
            String text = text(Set.of("profiles", "flamegraph"));

            assertTrue(text.contains("jeffrey/cost is CHEAP, MODERATE or EXPENSIVE."), text);
            assertFalse(text.contains("SLOW"), text);
            assertFalse(text.contains("jeffrey/requires"), text);
            for (String requirement : List.of("TRACES", "HEAP_DUMP_INDEXED", "AUTO_ANALYSIS", "HUB", "IDE_LINKED")) {
                assertFalse(text.contains(requirement), requirement + " named although not advertised: " + text);
            }
        }

        /** The requirement examples come from the advertised families only. */
        @Test
        void namesTheRequirementsOfTheAdvertisedFamilies() {
            String text = text(Set.of("profiles", "heap", "operations"));

            assertTrue(text.contains("jeffrey/cost is CHEAP, MODERATE, EXPENSIVE, or SLOW"), text);
            assertTrue(text.contains("jeffrey/requires, when present, lists what must already be in place, "
                    + "such as HEAP_DUMP_INDEXED."), text);
            assertFalse(text.contains("TRACES"), text);
        }

        @Test
        void saysEveryToolIsReadOnlyWhenNoWriterIsAdvertised() {
            String text = text(Set.of("profiles", "flamegraph"));

            assertTrue(text.contains("Every tool here is read-only"), text);
            assertFalse(text.contains("operationId"), text);
            assertFalse(text.contains("tasks/get"), text);
        }

        @Test
        void countsOnlyTheWritersAmongTheAdvertisedFamilies() {
            String text = text(Set.of("profiles", "recordings", "heap", "operations"));

            assertTrue(text.contains("Six tools are not read-only"), text);
            for (String writer : List.of("recordings_analyzeFile", "recordings_analyzeRecording",
                    "recordings_delete", "heap_prepare", "heap_oql", "operations_cancel")) {
                assertTrue(text.contains(writer), writer + " is missing: " + text);
            }
            for (String absent : List.of("hubs_download", "hubs_fetchFile", "jvm_autoAnalysis", "ide_link")) {
                assertFalse(text.contains(absent), absent + " is named although not advertised: " + text);
            }
            assertTrue(text.contains("The four that can take a while"), text);
        }

        @Test
        void speaksOfASingleSlowWriterInTheSingular() {
            String text = text(Set.of("profiles", "jvm", "operations"));

            assertTrue(text.contains("Two tools are not read-only"), text);
            assertTrue(text.contains("The one that can take a while - jvm_autoAnalysis with compute - "
                    + "waits up to 45 s, then answers with an operationId"), text);
            assertTrue(text.contains("A client that declared the MCP tasks extension gets a standard task "
                    + "after about 5 s instead"), text);
            assertFalse(text.contains("heap_prepare"), text);
        }

        @Test
        void saysTheWritersAnswerStraightAwayWhenNoneIsSlow() {
            String text = text(Set.of("profiles", "ide", "operations"));

            assertTrue(text.contains("Three tools are not read-only"), text);
            assertTrue(text.contains("They all answer straight away"), text);
            assertFalse(text.contains("45 s"), text);
            assertFalse(text.contains("tasks extension"), text);
        }

        @Test
        void keepsARuleOnlyWhenEveryFamilyItNamesIsAdvertised() {
            String withoutProfiles = text(Set.of("recordings", "operations"));
            String withProfiles = text(Set.of("profiles", "recordings", "operations"));

            assertTrue(withoutProfiles.contains("is followed, not called again"), withoutProfiles);
            assertFalse(withoutProfiles.contains("profiles_list"), withoutProfiles);
            assertTrue(withProfiles.contains("After a recordings_delete, profiles_list"), withProfiles);
        }

        @Test
        void namesOnlyTheAdvertisedDashboardOverviews() {
            String text = text(Set.of("profiles", "http", "io"));

            assertTrue(text.contains("starts at its overview (http_overview, io_overview)"), text);
            assertFalse(text.contains("jdbc_overview"), text);
        }

        @Test
        void exceptsOnlyAdvertisedFamiliesFromTheProfileIdRule() {
            String text = text(Set.of("profiles", "recordings", "heap", "operations"));

            assertTrue(text.contains("Every tool except profiles_list and the recordings_ and operations_ "
                    + "families takes a required profileId"), text);
        }
    }
}
