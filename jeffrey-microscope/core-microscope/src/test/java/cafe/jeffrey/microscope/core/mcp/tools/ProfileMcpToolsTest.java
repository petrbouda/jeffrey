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

import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileMcpToolsTest {

    /**
     * The tools build a UI link off the incoming request, the way ProfileMcpTools#link does.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }


    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    ProfileManager profileManager;

    @Mock
    ProfileFeaturesManager featuresManager;

    @Mock
    FlamegraphManager flamegraphManager;

    @Mock
    HeapDumpManager heapDumpManager;

    @Mock
    RecordingCommitResolver recordingCommitResolver;

    private ProfileMcpTools tools() {
        return new ProfileMcpTools(profileManager, recordingCommitResolver);
    }

    private void stubProfile(RecordingEventSource eventSource) {
        when(profileManager.info()).thenReturn(new ProfileInfo(
                "p-1", "proj-1", "ws-1", "Checkout run", eventSource,
                START, START.plusSeconds(120), START, true, false, "rec-1"));
        when(profileManager.featuresManager()).thenReturn(featuresManager);
        when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
        when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
        when(featuresManager.getDisabledFeatures()).thenReturn(List.of());
        when(flamegraphManager.allEventSummaries()).thenReturn(List.of());
        when(recordingCommitResolver.resolve("rec-1")).thenReturn(Optional.empty());
    }

    @Nested
    class Get {

        @Test
        void returnsTheProfilesIdentityAndSize() {
            stubProfile(RecordingEventSource.JDK);
            when(profileManager.sizeInBytes()).thenReturn(4096L);

            String result = tools().get();

            assertTrue(result.contains("\"profileId\":\"p-1\""));
            assertTrue(result.contains("\"sizeInBytes\":4096"));
        }

        /**
         * The commit is the one fact that lets a client in a checkout know whether it reads the code
         * that ran; it comes from the recording's tags, not from the profile itself.
         */
        @Test
        void reportsTheRecordingsCommitWhenItWasTagged() {
            stubProfile(RecordingEventSource.JDK);
            when(recordingCommitResolver.resolve("rec-1")).thenReturn(Optional.of("abc123"));

            assertTrue(tools().get().contains("\"recordingCommit\":\"abc123\""));
        }

        @Test
        void reportsAnUnknownCommitAsNullRatherThanOmittingIt() {
            stubProfile(RecordingEventSource.JDK);

            assertTrue(tools().get().contains("\"recordingCommit\":null"));
        }
    }

    @Nested
    class Features {

        @Test
        void reportsTheEventTypesTheProfileRecorded() {
            stubProfile(RecordingEventSource.JDK);
            when(flamegraphManager.allEventSummaries()).thenReturn(List.of(
                    new EventSummaryResult(new EventSummary(
                            "jdk.ExecutionSample", "Execution Sample", null, null,
                            1200, 0, true, false, List.of(), null, null))));

            String result = tools().features();

            assertTrue(result.contains("jdk.ExecutionSample"));
            assertTrue(result.contains("1200"));
        }

        /**
         * A profile whose heap-dump index has not been built cannot answer the heap tools, so the
         * client must be told before it tries rather than after it fails.
         */
        @Test
        void marksHeapDumpUnavailableWhenThereIsNoIndex() {
            stubProfile(RecordingEventSource.JDK);
            when(heapDumpManager.heapDumpExists()).thenReturn(false);

            assertTrue(tools().features().contains(FeatureType.HEAP_DUMP.name()));
        }

        /**
         * pprof profiles are aggregated and carry no per-sample timestamps, so anything time-resolved
         * would draw a single spike.
         */
        @Test
        void marksTimeResolvedViewsUnavailableForPprof() {
            stubProfile(RecordingEventSource.PPROF);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);

            String result = tools().features();

            assertTrue(result.contains(FeatureType.SUBSECOND.name()));
            assertTrue(result.contains(FeatureType.TIMESERIES.name()));
        }
    }

    @Nested
    class ViewLink {

        @Test
        void buildsALinkToTheNamedView() {
            stubProfile(RecordingEventSource.JDK);

            assertTrue(tools().viewLink("garbage-collection", null)
                    .endsWith("/profiles/p-1/garbage-collection"));
        }

        @Test
        void keepsAMultiSegmentViewIntact() {
            stubProfile(RecordingEventSource.JDK);

            assertTrue(tools().viewLink("heap-dump/leak-suspects", null)
                    .endsWith("/profiles/p-1/heap-dump/leak-suspects"));
        }

        /**
         * The one curated view that takes an argument; every other view ignores it rather than
         * carrying a parameter it does not read.
         */
        @Test
        void passesAnObjectIdOnlyToTheGcRootPathView() {
            stubProfile(RecordingEventSource.JDK);

            assertTrue(tools().viewLink("heap-dump/gc-root-path", "42").endsWith("?objectId=42"));
            assertFalse(tools().viewLink("garbage-collection", "42").contains("objectId"));
        }

        /**
         * A wrong guess has to fail as a wrong guess: a silently accepted name would become a link
         * that 404s into the SPA fallback, which the reader only discovers after clicking it.
         */
        @Test
        void refusesAnUnknownViewAndNamesTheValidOnes() {
            stubProfile(RecordingEventSource.JDK);

            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class, () -> tools().viewLink("gc", null));

            assertTrue(thrown.getMessage().contains("Unknown view 'gc'"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("garbage-collection"), thrown.getMessage());
        }
    }
}
