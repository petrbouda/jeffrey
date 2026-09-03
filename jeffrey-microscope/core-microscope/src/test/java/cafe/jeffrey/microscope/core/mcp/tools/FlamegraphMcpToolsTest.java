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

import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlamegraphMcpToolsTest {

    private static final String SAMPLE_TYPE_EXTRA = "sampleType";

    @Mock
    ProfileManager profileManager;

    @Mock
    FlamegraphManager flamegraphManager;

    private FlamegraphMcpTools tools() {
        return new FlamegraphMcpTools(
                profileManager, new JfrFlamegraphPanelProvider(), new StackSampleFlamegraphPanelProvider());
    }

    private static EventSummaryResult summary(String code, long samples, long weight) {
        return summary(code, samples, weight, Map.of());
    }

    private static EventSummaryResult summary(
            String code, long samples, long weight, Map<String, String> extras) {

        EventSummaryResult.SingleResult primary =
                new EventSummaryResult.SingleResult(code, code, null, null, samples, weight, false, extras);
        return new EventSummaryResult(code, code, primary, null);
    }

    private void profileOf(RecordingEventSource source, List<EventSummaryResult> summaries) {
        when(profileManager.info()).thenReturn(new ProfileInfo(
                "profile-1", "project-1", "workspace-1", "Profile", source,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
        when(flamegraphManager.eventSummaries()).thenReturn(summaries);
    }

    private static JsonNode entry(String result, String eventType) {
        JsonNode available = Json.mapper().readTree(result).get("available");
        for (JsonNode node : available) {
            if (node.get("eventType").asString().equals(eventType)) {
                return node;
            }
        }
        throw new AssertionError("no entry for " + eventType + " in " + result);
    }

    @Nested
    class JfrProfile {

        /**
         * The JFR grid always emits all eight sections, filling the empty ones with a zero-sample
         * placeholder. Offering those as valid eventTypes would send the caller after an empty tree,
         * so they are reported as gaps in what the profiler captured instead.
         */
        @Test
        void separatesRecordedTypesFromTheSectionsWithNoSamples() {
            profileOf(RecordingEventSource.JDK, List.of(
                    summary(EventTypeName.EXECUTION_SAMPLE, 4200, 0L),
                    summary(EventTypeName.OBJECT_ALLOCATION_SAMPLE, 130, 9_000_000L)));

            String result = tools().list();
            JsonNode root = Json.mapper().readTree(result);

            List<String> available = root.get("available").valueStream()
                    .map(node -> node.get("eventType").asString())
                    .toList();
            assertEquals(
                    List.of(EventTypeName.EXECUTION_SAMPLE, EventTypeName.OBJECT_ALLOCATION_SAMPLE),
                    available);

            List<String> notRecorded = root.get("notRecorded").valueStream()
                    .map(node -> node.get("section").asString())
                    .toList();
            assertEquals(
                    List.of("cpu-time", "method", "wall", "native-alloc", "native-leak", "blocking"),
                    notRecorded);
        }

        @Test
        void carriesTheExportArgumentDefaultsOfEachEventType() {
            profileOf(RecordingEventSource.JDK, List.of(
                    summary(EventTypeName.EXECUTION_SAMPLE, 4200, 0L),
                    summary(EventTypeName.OBJECT_ALLOCATION_SAMPLE, 130, 9_000_000L),
                    summary(EventTypeName.JAVA_MONITOR_ENTER, 12, 5_000_000_000L)));

            String result = tools().list();

            JsonNode allocation = entry(result, EventTypeName.OBJECT_ALLOCATION_SAMPLE);
            assertEquals(130, allocation.get("samples").asLong());
            assertEquals(9_000_000L, allocation.get("weight").asLong());
            assertEquals("bytes", allocation.get("weightUnit").asString());
            assertTrue(allocation.get("defaultUseWeight").asBoolean());

            JsonNode blocking = entry(result, EventTypeName.JAVA_MONITOR_ENTER);
            assertEquals("nanoseconds", blocking.get("weightUnit").asString());
            assertTrue(blocking.get("defaultUseWeight").asBoolean());

            // execution samples are counted, never weighed — the number and its unit are absent together
            JsonNode execution = entry(result, EventTypeName.EXECUTION_SAMPLE);
            assertTrue(execution.get("weight").isNull());
            assertNull(execution.get("weightUnit").asString(null));
            assertFalse(execution.get("defaultUseWeight").asBoolean());
        }

        /**
         * Reachable only because the placeholders are filtered out: the grid itself is never empty.
         */
        @Test
        void saysSoWhenNothingWasRecorded() {
            profileOf(RecordingEventSource.HEAP_DUMP, List.of());

            assertTrue(tools().list().startsWith("This profile has no flamegraph-capable event types."));
        }
    }

    @Nested
    class StackSampleImport {

        /**
         * pprof and OTLP carry their own sample dimensions rather than JFR event types. Running them
         * through the JFR catalog would report every dimension they do have as missing.
         */
        @Test
        void listsTheDimensionsOfAnImportedProfile() {
            profileOf(RecordingEventSource.PPROF, List.of(
                    summary("cpu", 900, 0L, Map.of(SAMPLE_TYPE_EXTRA, "cpu/nanoseconds")),
                    summary("alloc_space", 40, 2048L, Map.of(SAMPLE_TYPE_EXTRA, "alloc_space/bytes"))));

            String result = tools().list();
            JsonNode root = Json.mapper().readTree(result);

            List<String> available = root.get("available").valueStream()
                    .map(node -> node.get("eventType").asString())
                    .toList();
            assertEquals(List.of("cpu", "alloc_space"), available);
            assertTrue(root.get("notRecorded").isEmpty());

            JsonNode alloc = entry(result, "alloc_space");
            assertEquals("bytes", alloc.get("weightUnit").asString());
            assertEquals(2048L, alloc.get("weight").asLong());
        }
    }
}
