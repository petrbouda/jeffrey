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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
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
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        return new HeapDiffMcpTools(profileManager, baselineResolver);
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

    @Nested
    class Diff {

        @Test
        void ranksTheClassesThatGrewWithBothSidesOfTheirCounts() {
            twoIndexedDumps();

            String out = tools().diff(BASELINE_ID, null);

            assertTrue(out.contains(SESSION_CLASS), out);
            assertTrue(out.contains("\"countDelta\":400"), out);
            assertTrue(out.contains("\"bytesDelta\":40000"), out);
            assertTrue(out.contains("\"primaryCount\":500"), out);
            assertTrue(out.contains("\"baselineCount\":100"), out);
        }

        @Test
        void carriesTheWholeDumpTotalsBesideThePerClassRows() {
            twoIndexedDumps();

            String out = tools().diff(BASELINE_ID, null);

            assertTrue(out.contains("\"instanceCountDelta\":400"), out);
            assertTrue(out.contains("\"shallowBytesDelta\":40000"), out);
            assertTrue(out.contains("heap-dump/diff"), out);
        }

        /**
         * The cap is what the answer is ranked for: the classes are ordered by absolute byte delta, so
         * a top of one has to be the one that moved most rather than the first one read.
         */
        @Test
        void keepsOnlyTheBiggestMoversWhenATopIsGiven() {
            twoIndexedDumps();

            String out = tools().diff(BASELINE_ID, 1);

            assertTrue(out.contains(SESSION_CLASS), out);
            assertFalse(out.contains(CACHE_CLASS), out);
        }

        /**
         * Math.clamp refuses a lower bound above the value, so a zero has to become one rather than
         * reaching HeapDumpDiffService, whose own check would turn it into a failure.
         */
        @Test
        void readsANonPositiveTopAsOne() {
            twoIndexedDumps();

            String out = tools().diff(BASELINE_ID, 0);

            assertTrue(out.contains(SESSION_CLASS), out);
            assertFalse(out.contains(CACHE_CLASS), out);
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

            String out = tools().diff(BASELINE_ID, null);

            assertTrue(out.contains("no heap dump to compare"), out);
            assertTrue(out.contains("profiles_list"), out);
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

            String out = tools().diff(BASELINE_ID, null);

            assertTrue(out.contains("still being indexed"), out);
            assertFalse(out.contains("no heap dump to compare"), out);
        }

        @Test
        void namesTheBaselineWhenItIsTheSideWithoutADump() {
            indexedDump(primaryDump, summary(60_000, 700), entry(SESSION_CLASS, 500, 50_000));
            when(baselineDump.heapDumpExists()).thenReturn(false);

            String out = tools().diff(BASELINE_ID, null);

            assertTrue(out.contains("baseline profile '" + BASELINE_ID + "' has no heap dump"), out);
        }

        @Test
        void namesTheBaselineWhenItIsTheSideStillBeingIndexed() {
            indexedDump(primaryDump, summary(60_000, 700), entry(SESSION_CLASS, 500, 50_000));
            when(baselineDump.heapDumpExists()).thenReturn(true);
            when(baselineDump.isCacheReady()).thenReturn(false);

            String out = tools().diff(BASELINE_ID, null);

            assertTrue(out.contains("baseline profile " + BASELINE_ID), out);
            assertTrue(out.contains("still being indexed"), out);
        }
    }
}
