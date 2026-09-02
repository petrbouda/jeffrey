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

import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileMcpToolsTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    ProfileManager profileManager;

    @Mock
    ProfileFeaturesManager featuresManager;

    @Mock
    FlamegraphManager flamegraphManager;

    @Mock
    HeapDumpManager heapDumpManager;

    private ProfileMcpTools tools() {
        return new ProfileMcpTools(profileManager);
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
}
