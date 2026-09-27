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

import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.JsonNode;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HeapDiffMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String BASELINE_ID = "p-0";
    private static final String SESSION_CLASS = "com.acme.Session";
    private static final String CACHE_CLASS = "com.acme.Cache";

    @Mock
    ProfileManager profileManager;

    @Mock
    ProfileManager baselineProfileManager;

    @Mock
    HeapDumpManager primaryDump;

    @Mock
    HeapDumpManager baselineDump;

    @Mock
    Function<String, ProfileManager> baselineResolver;

    /**
     * The answer carries a link into the diff view, which UiLinks builds off the request being served.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.HEAP_DUMP,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.heapDumpManager()).thenReturn(primaryDump);
        when(baselineProfileManager.heapDumpManager()).thenReturn(baselineDump);
        when(baselineResolver.apply(any())).thenReturn(baselineProfileManager);
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private HeapDiffMcpTools tools() {
        return new HeapDiffMcpTools(profileManager, baselineResolver, EVERY_FAMILY);
    }

    private static HeapSummary summary(long totalBytes, long totalInstances) {
        return new HeapSummary(totalBytes, totalInstances, 12, 4, Instant.EPOCH);
    }

    private static ClassHistogramEntry entry(String className, long instances, long bytes) {
        return new ClassHistogramEntry(className, instances, bytes, List.of());
    }

    private void indexedDump(HeapDumpManager dump, HeapSummary summary, ClassHistogramEntry... entries) {
        when(dump.heapDumpExists()).thenReturn(true);
        when(dump.isCacheReady()).thenReturn(true);
        when(dump.getSummary()).thenReturn(summary);
        when(dump.getClassHistogram(anyInt(), any())).thenReturn(List.of(entries));
    }

    private void twoIndexedDumps() {
        indexedDump(primaryDump, summary(60_000, 700),
                entry(SESSION_CLASS, 500, 50_000),
                entry(CACHE_CLASS, 10, 10_500));
        indexedDump(baselineDump, summary(20_000, 300),
                entry(SESSION_CLASS, 100, 10_000),
                entry(CACHE_CLASS, 10, 10_000));
    }

    /** The answer, checked against the schema the tool advertises, its link and its next calls. */
    private static JsonNode conforming(McpToolResult result) {
        JsonNode structured = result.structuredContent();
        Class<? extends Record> schema = Arrays.stream(HeapDiffMcpTools.class.getMethods())
                .filter(method -> method.getName().equals("diff"))
                .findFirst()
                .orElseThrow()
                .getAnnotation(McpOutputSchema.class)
                .value();
        McpSchemaConformance.assertConforms(structured, McpSchemaGenerator.schemaOf(schema));
        UiLinkRoutes.assertResolves(structured.get("uiLink").asString());
        assertEquals(Json.toString(structured), result.text(), "the text is the record's own JSON");
        McpNextToolConformance.assertFollowable(structured, HeapDumpMcpToolsTest.reachable());
        return structured;
    }

    private static JsonNode call(JsonNode structured, String tool) {
        for (JsonNode call : structured.get("followUp").get("nextTools")) {
            if (call.get("tool").asString().equals(tool)) {
                return call.get("arguments");
            }
        }
        throw new AssertionError("no call to " + tool + " in " + structured);
    }

    @Nested
    class Diff {

        @Test
        void ranksTheClassesThatGrewWithBothSidesOfTheirCounts() {
            twoIndexedDumps();

            JsonNode out = conforming(tools().diff(BASELINE_ID, null));

            assertEquals("OK", out.get("status").asString());
            JsonNode first = out.get("classes").get(0);
            assertEquals(SESSION_CLASS, first.get("className").asString());
            assertEquals(400, first.get("countDelta").asLong());
            assertEquals(40_000, first.get("bytesDelta").asLong());
            assertEquals(500, first.get("primaryCount").asLong());
            assertEquals(100, first.get("baselineCount").asLong());
            assertEquals(SESSION_CLASS, call(out, "heap_browseClassInstances").get("className").asString());
        }

        @Test
        void carriesTheWholeDumpTotalsBesideThePerClassRows() {
            twoIndexedDumps();

            JsonNode out = conforming(tools().diff(BASELINE_ID, null));

            assertEquals(400, out.get("instanceCountDelta").asLong());
            assertEquals(40_000, out.get("shallowBytesDelta").asLong());
            assertEquals(60_000, out.get("primaryDump").get("totalBytes").asLong());
            assertEquals(0, out.get("primaryDump").get("takenAtEpochMs").asLong());
        }

        /** The diff page reads its baseline from the link, so the link opens this very comparison. */
        @Test
        void linksTheDiffPageWithTheBaseline() {
            twoIndexedDumps();

            JsonNode out = conforming(tools().diff(BASELINE_ID, null));

            assertTrue(out.get("uiLink").asString().endsWith("/profiles/" + PROFILE_ID + "/heap-dump/diff?baseline="
                    + BASELINE_ID), out.toString());
        }

        /**
         * The cap is what the answer is ranked for: the classes are ordered by absolute byte delta, so
         * a top of one has to be the one that moved most rather than the first one read.
         */
        @Test
        void keepsOnlyTheBiggestMoversWhenATopIsGiven() {
            twoIndexedDumps();

            JsonNode out = conforming(tools().diff(BASELINE_ID, 1));

            assertEquals(1, out.get("classes").size());
            assertEquals(SESSION_CLASS, out.get("classes").get(0).get("className").asString());
            assertTrue(out.get("omittedClasses").isNull(), "a list at its cap cannot say what it left out");
        }

        /**
         * One clamp convention across every tool: zero or below means the default rather than one
         * class, and never reaches HeapDumpDiffService, whose own check would turn it into a failure.
         */
        @Test
        void readsANonPositiveTopAsTheDefault() {
            twoIndexedDumps();

            JsonNode out = conforming(tools().diff(BASELINE_ID, 0));

            assertEquals(2, out.get("classes").size());
            assertEquals(0, out.get("omittedClasses").asInt());
        }

        @Test
        void trimsTheBaselineIdBeforeResolvingTheProfile() {
            twoIndexedDumps();

            tools().diff("  " + BASELINE_ID + "  ", null);

            verify(baselineResolver).apply(BASELINE_ID);
        }
    }

    @Nested
    class Refusals {

        /**
         * The argument is only refused once this profile turns out to have a dump at all: a JFR
         * recording asking for a comparison is answered with what it is missing, not with a complaint
         * about the argument it left out of a call that could never have worked.
         */
        @Test
        void refusesAMissingBaselineNamingWhatItIsFor() {
            indexedDump(primaryDump, summary(60_000, 700), entry(SESSION_CLASS, 500, 50_000));

            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class, () -> tools().diff(null, null));

            assertTrue(thrown.getMessage().contains("baselineProfileId is required"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("earlier heap dump"), thrown.getMessage());
        }

        @Test
        void refusesABlankBaselineTheSameWay() {
            indexedDump(primaryDump, summary(60_000, 700), entry(SESSION_CLASS, 500, 50_000));

            assertThrows(IllegalArgumentException.class, () -> tools().diff("   ", null));
        }

        /**
         * A profile that is not a heap dump at all is answered before the baseline is even resolved:
         * resolving it would load a second profile for a comparison that cannot happen.
         */
        @Test
        void saysWhichProfilesAreHeapDumpsWhenThisOneIsNot() {
            when(primaryDump.heapDumpExists()).thenReturn(false);

            JsonNode out = conforming(tools().diff(BASELINE_ID, null));

            assertEquals("NO_HEAP_DUMP", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("no heap dump to compare"), out.toString());
            assertTrue(out.get("primaryDump").isNull());
            assertTrue(out.get("classes").isEmpty());
            assertTrue(out.get("omittedClasses").isNull(), "nothing was compared, so nothing was left out");
            call(out, "profiles_list");
            verify(baselineResolver, never()).apply(any());
        }

        /**
         * A dump that exists but has not been indexed is a different answer from one that does not
         * exist, and the difference is what the reader can act on.
         */
        @Test
        void tellsAnUnindexedDumpFromAMissingOne() {
            when(primaryDump.heapDumpExists()).thenReturn(true);
            when(primaryDump.isCacheReady()).thenReturn(false);

            JsonNode out = conforming(tools().diff(BASELINE_ID, null));

            assertEquals("NOT_INDEXED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("still being indexed"), out.toString());
            assertEquals(PROFILE_ID, call(out, "heap_prepare").get("profileId").asString());
        }

        @Test
        void namesTheBaselineWhenItIsTheSideWithoutADump() {
            indexedDump(primaryDump, summary(60_000, 700), entry(SESSION_CLASS, 500, 50_000));
            when(baselineDump.heapDumpExists()).thenReturn(false);

            JsonNode out = conforming(tools().diff(BASELINE_ID, null));

            assertEquals("BASELINE_NO_HEAP_DUMP", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("baseline profile '" + BASELINE_ID + "' has no heap dump"),
                    out.toString());
        }

        @Test
        void namesTheBaselineWhenItIsTheSideStillBeingIndexed() {
            indexedDump(primaryDump, summary(60_000, 700), entry(SESSION_CLASS, 500, 50_000));
            when(baselineDump.heapDumpExists()).thenReturn(true);
            when(baselineDump.isCacheReady()).thenReturn(false);

            JsonNode out = conforming(tools().diff(BASELINE_ID, null));

            assertEquals("BASELINE_NOT_INDEXED", out.get("status").asString());
            assertTrue(out.get("omittedClasses").isNull());
            assertTrue(out.get("reason").asString().contains("baseline profile " + BASELINE_ID), out.toString());
            assertEquals(BASELINE_ID, call(out, "heap_prepare").get("profileId").asString());
        }
    }
}
