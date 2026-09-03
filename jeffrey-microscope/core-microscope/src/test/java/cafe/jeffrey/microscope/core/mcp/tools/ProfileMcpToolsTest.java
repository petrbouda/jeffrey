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

import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.microscope.persistence.api.RecordingTagsRepository;
import cafe.jeffrey.microscope.core.mcp.RecordingCommitResolver;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileConfigurationManager;
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    @Mock
    ProfileConfigurationManager configurationManager;

    @Mock
    RecordingCommitResolver commitResolver;

    @Mock
    RecordingTagsRepository recordingTagsRepository;

    private ProfileMcpTools tools() {
        return new ProfileMcpTools(profileManager, commitResolver, recordingTagsRepository);
    }

    private void stubProfile(RecordingEventSource eventSource) {
        stubProfile(eventSource, "rec-1");
    }

    private void stubProfile(RecordingEventSource eventSource, String recordingId) {
        when(profileManager.info()).thenReturn(new ProfileInfo(
                "p-1", "proj-1", "ws-1", "Checkout run", eventSource,
                START, START.plusSeconds(120), START, true, false, recordingId));
        when(profileManager.profileConfigurationManager()).thenReturn(configurationManager);
        when(configurationManager.configuration()).thenReturn(Json.createObject());
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

    @Nested
    class BuildInfo {

        private ObjectNode jvmInformation(String javaArguments) {
            ObjectNode jvm = Json.createObject();
            jvm.put("JVM Name", "OpenJDK 64-Bit Server VM");
            jvm.put("JVM Version", "25.0.1+9");
            jvm.put("JVM Command Line Arguments", "-Xmx4g -XX:+UseZGC");
            jvm.put("Java Application Arguments", javaArguments);

            ObjectNode configuration = Json.createObject();
            configuration.set("JVM Information", jvm);
            return configuration;
        }

        @Test
        void reportsTheCommitTheRecordingWasTaggedWith() {
            stubProfile(RecordingEventSource.JDK);
            when(commitResolver.resolve("rec-1")).thenReturn(Optional.of("9f21c0e"));
            when(recordingTagsRepository.listForRecording("rec-1")).thenReturn(List.of(
                    new RecordingTag("git.commit", "9f21c0e"),
                    new RecordingTag("service", "checkout")));

            String result = tools().buildInfo();

            assertTrue(result.contains("\"recordingCommit\":\"9f21c0e\""));
            assertTrue(result.contains("\"key\":\"service\""));
        }

        /**
         * The common case for a recording made by hand. Reported as unknown rather than omitted, so an
         * absent field is not read as a match.
         */
        @Test
        void admitsWhenNothingIdentifiesTheBuild() {
            stubProfile(RecordingEventSource.JDK);
            when(commitResolver.resolve("rec-1")).thenReturn(Optional.empty());
            when(recordingTagsRepository.listForRecording("rec-1")).thenReturn(List.of());

            String result = tools().buildInfo();

            assertTrue(result.contains("\"recordingCommit\":null"));
            assertTrue(result.contains("\"jvm\":null"));
        }

        @Test
        void carriesTheCommandLineTheApplicationRanWith() {
            stubProfile(RecordingEventSource.JDK);
            when(configurationManager.configuration())
                    .thenReturn(jvmInformation("cafe.jeffrey.example.OrdersApplication"));

            String result = tools().buildInfo();

            assertTrue(result.contains("cafe.jeffrey.example.OrdersApplication"));
            assertTrue(result.contains("-XX:+UseZGC"));
        }

        /**
         * A Quick Analysis profile was opened straight from a file: there is no recording to ask about.
         */
        @Test
        void asksForNoTagsWhenTheProfileHasNoRecording() {
            stubProfile(RecordingEventSource.JDK, null);

            assertTrue(tools().buildInfo().contains("\"recordingTags\":[]"));
        }
    }
}
