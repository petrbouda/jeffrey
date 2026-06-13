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

package cafe.jeffrey.profile.manager;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadActivity;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoaderStat;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadingOverview;
import cafe.jeffrey.profile.manager.model.classloading.RedefinitionData;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassLoadingManagerImpl")
class ClassLoadingManagerImplTest {

    @Mock
    ProfileEventRepository eventRepository;

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T00:01:00Z");

    private static final ProfileInfo PROFILE_INFO = new ProfileInfo(
            "test-id", "project-1", "workspace-1", "test-profile",
            null, START, END, START, true, false, null);

    private ClassLoadingManagerImpl manager() {
        return new ClassLoadingManagerImpl(PROFILE_INFO, eventRepository, eventStreamRepository);
    }

    /**
     * Drives whatever {@link RecordBuilder} the manager hands to {@code genericStreaming} with the
     * supplied synthetic records, then returns its built result — exercising the real builders.
     */
    @SuppressWarnings("unchecked")
    private void stubStreaming(GenericRecord... records) {
        when(eventStreamRepository.genericStreaming(any(), any())).thenAnswer(invocation -> {
            RecordBuilder<GenericRecord, Object> builder = invocation.getArgument(1);
            for (GenericRecord record : records) {
                builder.onRecord(record);
            }
            return builder.build();
        });
    }

    private static GenericRecord record(long secondsFromStart, Duration duration, ObjectNode fields) {
        return new GenericRecord(
                Type.CLASS_LOADER_STATISTICS, "label", START,
                Duration.ofSeconds(secondsFromStart), duration,
                null, null, 0L, 0L, fields);
    }

    private static ObjectNode loaderFields(
            String classLoader, String parent, long loaderData,
            long classCount, long chunkSize, long blockSize, long hiddenCount, long hiddenChunk) {
        ObjectNode node = Json.createObject();
        if (classLoader == null) {
            node.putNull("classLoader");
        } else {
            node.put("classLoader", classLoader);
        }
        if (parent == null) {
            node.putNull("parentClassLoader");
        } else {
            node.put("parentClassLoader", parent);
        }
        node.put("classLoaderData", loaderData);
        node.put("classCount", classCount);
        node.put("chunkSize", chunkSize);
        node.put("blockSize", blockSize);
        node.put("hiddenClassCount", hiddenCount);
        node.put("hiddenChunkSize", hiddenChunk);
        return node;
    }

    @Nested
    @DisplayName("overview()")
    class Overview {

        @Test
        @DisplayName("Aggregates loaded/unloaded counts, loader metaspace and hidden classes")
        void aggregates() {
            ObjectNode latest = Json.createObject();
            latest.put("loadedClassCount", 1000L);
            latest.put("unloadedClassCount", 50L);
            when(eventRepository.latestJsonFields(Type.CLASS_LOADING_STATISTICS)).thenReturn(Optional.of(latest));
            when(eventRepository.containsEventType(Type.CLASS_LOAD)).thenReturn(false);
            when(eventRepository.containsEventType(Type.CLASS_REDEFINITION)).thenReturn(true);

            stubStreaming(
                    record(0, null, loaderFields("AppLoader", "PlatformLoader", 1, 80, 2000, 1800, 3, 100)),
                    record(0, null, loaderFields("PlatformLoader", null, 2, 40, 1000, 900, 0, 0)));

            ClassLoadingOverview overview = manager().overview();

            assertEquals(1000, overview.totalLoaded());
            assertEquals(50, overview.totalUnloaded());
            assertEquals(950, overview.currentlyLoaded());
            assertEquals(2, overview.classLoaderCount());
            assertEquals(3000, overview.metaspaceUsedBytes());
            assertEquals(3, overview.hiddenClassCount());
            assertFalse(overview.hasClassLoadEvents());
            assertTrue(overview.hasRedefinitionEvents());
        }

        @Test
        @DisplayName("Falls back to zeros when no ClassLoadingStatistics event is present")
        void noStatistics() {
            when(eventRepository.latestJsonFields(Type.CLASS_LOADING_STATISTICS)).thenReturn(Optional.empty());
            when(eventRepository.containsEventType(Type.CLASS_LOAD)).thenReturn(false);
            when(eventRepository.containsEventType(Type.CLASS_REDEFINITION)).thenReturn(false);
            stubStreaming();

            ClassLoadingOverview overview = manager().overview();

            assertEquals(0, overview.totalLoaded());
            assertEquals(0, overview.currentlyLoaded());
            assertEquals(0, overview.classLoaderCount());
        }
    }

    @Nested
    @DisplayName("classLoaders()")
    class ClassLoaders {

        @Test
        @DisplayName("Keeps the latest snapshot per loader and orders by descending metaspace")
        void dedupesAndSorts() {
            stubStreaming(
                    record(0, null, loaderFields("AppLoader", "PlatformLoader", 1, 10, 500, 400, 0, 0)),
                    record(1, null, loaderFields("AppLoader", "PlatformLoader", 1, 25, 1500, 1400, 2, 50)),
                    record(0, null, loaderFields("PlatformLoader", null, 2, 40, 1000, 900, 0, 0)));

            List<ClassLoaderStat> loaders = manager().classLoaders();

            assertEquals(2, loaders.size());
            // Loader 1's last snapshot (1500) wins and outranks loader 2 (1000).
            assertEquals("AppLoader", loaders.get(0).name());
            assertEquals(1500, loaders.get(0).metaspaceBytes());
            assertEquals(25, loaders.get(0).classCount());
            assertEquals(2, loaders.get(0).hiddenClassCount());
            assertEquals("PlatformLoader", loaders.get(1).name());
            assertEquals(1000, loaders.get(1).metaspaceBytes());
        }

        @Test
        @DisplayName("Labels the bootstrap loader when the classLoader field is absent")
        void bootstrapLabel() {
            stubStreaming(record(0, null, loaderFields(null, null, 0, 5, 100, 90, 0, 0)));

            List<ClassLoaderStat> loaders = manager().classLoaders();

            assertEquals(1, loaders.size());
            assertEquals("Bootstrap Class Loader", loaders.getFirst().name());
        }
    }

    @Nested
    @DisplayName("classLoadActivity()")
    class ClassLoadActivityTests {

        @Test
        @DisplayName("Returns empty without querying the stream when jdk.ClassLoad is absent")
        void emptyWhenAbsent() {
            when(eventRepository.containsEventType(Type.CLASS_LOAD)).thenReturn(false);

            ClassLoadActivity activity = manager().classLoadActivity();

            assertEquals(0, activity.totalCount());
            assertTrue(activity.slowest().isEmpty());
            verify(eventStreamRepository, never()).genericStreaming(any(), any());
        }

        @Test
        @DisplayName("Counts loads and orders the slowest first")
        void slowestFirst() {
            when(eventRepository.containsEventType(Type.CLASS_LOAD)).thenReturn(true);

            ObjectNode fast = Json.createObject();
            fast.put("loadedClass", "com.example.Fast");
            fast.put("definingClassLoader", "AppLoader");
            ObjectNode slow = Json.createObject();
            slow.put("loadedClass", "com.example.Slow");
            slow.put("definingClassLoader", "AppLoader");

            stubStreaming(
                    record(0, Duration.ofNanos(1_000), fast),
                    record(0, Duration.ofNanos(9_000), slow));

            ClassLoadActivity activity = manager().classLoadActivity();

            assertEquals(2, activity.totalCount());
            assertEquals("com.example.Slow", activity.slowest().get(0).className());
            assertEquals(9_000, activity.slowest().get(0).durationNanos());
            assertEquals("com.example.Fast", activity.slowest().get(1).className());
        }
    }

    @Nested
    @DisplayName("redefinitions()")
    class Redefinitions {

        @Test
        @DisplayName("Maps ClassRedefinition and RetransformClasses events")
        void maps() {
            ObjectNode redef = Json.createObject();
            redef.put("redefinedClass", "java.lang.String");
            redef.put("classModificationCount", 2);
            redef.put("redefinitionId", 7L);

            ObjectNode retransform = Json.createObject();
            retransform.put("redefinitionId", 7L);
            retransform.put("classCount", 18);
            retransform.put("duration", 81_000_000L);

            when(eventRepository.eventsByTypeWithFields(Type.CLASS_REDEFINITION))
                    .thenReturn(List.of((JsonNode) redef));
            when(eventRepository.eventsByTypeWithFields(Type.RETRANSFORM_CLASSES))
                    .thenReturn(List.of((JsonNode) retransform));

            RedefinitionData data = manager().redefinitions();

            assertEquals(1, data.redefinitions().size());
            assertEquals("java.lang.String", data.redefinitions().getFirst().className());
            assertEquals(2, data.redefinitions().getFirst().modificationCount());
            assertEquals(7, data.redefinitions().getFirst().redefinitionId());

            assertEquals(1, data.retransforms().size());
            assertEquals(18, data.retransforms().getFirst().classCount());
            assertEquals(81_000_000L, data.retransforms().getFirst().durationNanos());
        }
    }

    @Nested
    @DisplayName("timeline()")
    class Timeline {

        @Test
        @DisplayName("Produces loaded and unloaded series carrying the latest gauge forward")
        void buildsSeries() {
            ObjectNode firstSample = Json.createObject();
            firstSample.put("loadedClassCount", 1000L);
            firstSample.put("unloadedClassCount", 50L);

            stubStreaming(record(2, null, firstSample));

            TimeseriesData timeline = manager().timeline();

            assertEquals(2, timeline.series().size());
            SingleSerie loaded = timeline.series().get(0);
            SingleSerie unloaded = timeline.series().get(1);
            assertEquals("Loaded Classes", loaded.name());
            assertEquals("Unloaded Classes", unloaded.name());
            // currentlyLoaded = 1000 - 50 carried across the recording window.
            long maxLoaded = loaded.data().stream().mapToLong(point -> point.get(1)).max().orElse(0);
            assertEquals(950, maxLoaded);
        }

        @Test
        @DisplayName("Delegates streaming with the ClassLoadingStatistics event type")
        void delegates() {
            SingleSerie empty = new SingleSerie("x", List.of());
            when(eventStreamRepository.genericStreaming(any(), any()))
                    .thenReturn(new TimeseriesData(empty, empty));

            TimeseriesData result = manager().timeline();

            assertSame(empty, result.series().getFirst());
            verify(eventStreamRepository).genericStreaming(any(), any());
        }
    }
}
