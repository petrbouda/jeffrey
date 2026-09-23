/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import cafe.jeffrey.microscope.model.EventSourceResolver;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.settings.ActiveSetting;
import cafe.jeffrey.microscope.model.settings.ActiveSettings;
import cafe.jeffrey.provider.profile.api.DatabaseWriter;
import cafe.jeffrey.provider.profile.api.EventTypeBuilder;
import cafe.jeffrey.provider.profile.api.EnhancedEventType;
import cafe.jeffrey.provider.profile.api.EventThreadWithHash;
import cafe.jeffrey.provider.profile.jdbc.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

public class WriterResultCollector {

    private final DatabaseWriter<EnhancedEventType> eventTypeWriter;
    private final DatabaseWriter<EventThreadWithHash> threadWriter;

    private EventWriterResult combined = new EventWriterResult(
            new ArrayList<>(), new ArrayList<>(), new HashMap<>(), new HashSet<>(), Instant.MIN);

    public WriterResultCollector(
            DatabaseWriter<EnhancedEventType> eventTypeWriter,
            DatabaseWriter<EventThreadWithHash> threadWriter) {

        this.eventTypeWriter = eventTypeWriter;
        this.threadWriter = threadWriter;
    }

    public void add(EventWriterResult newResults) {
        List<EventTypeBuilder> events =
                combineEventTypes(combined.eventTypes(), newResults.eventTypes());

        Map<String, ActiveSetting> activeSettings =
                combineActiveSettings(combined.activeSettings(), newResults.activeSettings());

        List<EventThreadWithHash> threads = new ArrayList<>();
        threads.addAll(combined.eventThreads());
        threads.addAll(newResults.eventThreads());

        // To resolve the latest event, figure out when the processing finished
        Instant latestEvent = resolveLatestEvent(combined, newResults);

        // Combine information if the event types contain stacktraces
        Set<String> containStacktraces = combined.eventTypesContainingStacktraces();
        containStacktraces.addAll(newResults.eventTypesContainingStacktraces());

        this.combined = new EventWriterResult(threads, events, activeSettings, containStacktraces, latestEvent);
    }

    public void combine() {
        List<EventTypeEnhancer> enhancers = resolveEventTypeEnhancers(
                new ActiveSettings(combined.activeSettings()));

        for (EventTypeBuilder eventTypeBuilder : combined.eventTypes()) {
            applyEnhancers(enhancers, eventTypeBuilder);
            ActiveSetting activeSetting = combined.activeSettings()
                    .get(eventTypeBuilder.getEventType().name());

            if (activeSetting != null) {
                eventTypeBuilder.putParams(activeSetting.params());
            }

            boolean containsStackTraces = combined.eventTypesContainingStacktraces()
                    .contains(eventTypeBuilder.getEventType().name());

            eventTypeBuilder.withContainsStackTraces(containsStackTraces);

            eventTypeWriter.insert(eventTypeBuilder.build());
        }

        // Threads names can be cleaned/modified by several approaches to ensure the better consistency and completeness
        // e.g. missing names [tid=25432], shorter names from AsyncProfiler (based on Linux filesystem info), ...
        // In most cases, it's about JVM threads (GC, JIT, ...), JDK-based JFR events provides valid threads names
        List<EventThreadWithHash> modifiedThreads = new EventThreadCleaner()
                .clean(combined.eventThreads());

        modifiedThreads.forEach(threadWriter::insert);
    }

    private List<EventTypeEnhancer> resolveEventTypeEnhancers(ActiveSettings settings) {
        return List.of(
                // Complex enhancers with unique logic
                new ExecutionSamplesExtraEnhancer(settings),
                new WallClockSamplesWeightEnhancer(settings),
                new ExecutionSamplesWeightEnhancer(settings),

                // Settings-based source enhancers (parameterized)
                new SettingsBasedSourceEnhancer(
                        Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
                        ActiveSettings::allocationSupportedBy,
                        settings),
                new SettingsBasedSourceEnhancer(
                        Type.JAVA_MONITOR_ENTER,
                        ActiveSettings::monitorEnterSupportedBy,
                        settings),
                new SettingsBasedSourceEnhancer(
                        Type.THREAD_PARK,
                        ActiveSettings::threadParkSupportedBy,
                        settings)
        );
    }

    private static void applyEnhancers(List<EventTypeEnhancer> enhancers, EventTypeBuilder builder) {
        Type type = Type.fromCode(builder.getEventType().name());

        // Source: use the one the reader set explicitly (pprof / OTLP, whose raw codes carry no namespace
        // to infer it from); otherwise derive it from the code namespace (JFR: profiler.* -> async-profiler,
        // else JDK). The subtype/settings enhancers below run afterwards and override it where needed
        // (e.g. the jdk.ExecutionSample subtype, allocation/monitor events captured by async-profiler).
        RecordingEventSource explicitSource = builder.getEventType().source();
        builder.withSource(explicitSource != null
                ? explicitSource
                : EventSourceResolver.fromEventTypeName(type.code()));

        if (enhancers != null) {
            for (EventTypeEnhancer enhancer : enhancers) {
                if (enhancer.isApplicable(type)) {
                    enhancer.apply(builder);
                }
            }
        }
    }

    private static List<EventTypeBuilder> combineEventTypes(
            List<EventTypeBuilder> partial1,
            List<EventTypeBuilder> partial2) {

        List<EventTypeBuilder> mergedBuilders = new ArrayList<>();
        Stream.concat(partial1.stream(), partial2.stream()).forEach(builder -> {
            Optional<EventTypeBuilder> builderOpt = mergedBuilders.stream()
                    .filter(type -> type.getEventType().name().equals(builder.getEventType().name()))
                    .findFirst();

            if (builderOpt.isEmpty()) {
                mergedBuilders.add(builder);
            }
        });

        return mergedBuilders;
    }

    private static Map<String, ActiveSetting> combineActiveSettings(
            Map<String, ActiveSetting> partial1,
            Map<String, ActiveSetting> partial2) {

        Map<String, ActiveSetting> combined = new HashMap<>(partial1);
        for (Map.Entry<String, ActiveSetting> entry : partial2.entrySet()) {
            combined.merge(entry.getKey(), entry.getValue(), WriterResultCollector::mergeActiveSetting);
        }
        return combined;
    }

    private static Instant resolveLatestEvent(EventWriterResult first, EventWriterResult second) {
        return first.latestEvent().isAfter(second.latestEvent()) ? first.latestEvent() : second.latestEvent();
    }

    private static ActiveSetting mergeActiveSetting(ActiveSetting setting1, ActiveSetting setting2) {
        Map<String, String> params = new HashMap<>();
        if (setting1.enabled()) {
            params.putAll(setting1.params());
        }
        if (setting2.enabled()) {
            params.putAll(setting2.params());
        }
        return new ActiveSetting(setting1.event(), params);
    }
}
