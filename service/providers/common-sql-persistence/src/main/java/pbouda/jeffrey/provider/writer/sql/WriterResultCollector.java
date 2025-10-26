/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.writer.sql;

import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.settings.ActiveSetting;
import pbouda.jeffrey.common.settings.ActiveSettings;
import pbouda.jeffrey.provider.api.DatabaseWriter;
import pbouda.jeffrey.provider.api.model.EventTypeBuilder;
import pbouda.jeffrey.provider.api.model.writer.EnhancedEventType;
import pbouda.jeffrey.provider.api.model.writer.EventThreadWithId;
import pbouda.jeffrey.provider.writer.sql.enhancer.EventTypeEnhancer;
import pbouda.jeffrey.provider.writer.sql.enhancer.ExecutionSamplesExtraEnhancer;
import pbouda.jeffrey.provider.writer.sql.enhancer.ExecutionSamplesWeightEnhancer;
import pbouda.jeffrey.provider.writer.sql.enhancer.MonitorEnterExtraEnhancer;
import pbouda.jeffrey.provider.writer.sql.enhancer.MonitorWaitExtraEnhancer;
import pbouda.jeffrey.provider.writer.sql.enhancer.NativeMallocAllocationSamplesExtraEnhancer;
import pbouda.jeffrey.provider.writer.sql.enhancer.ThreadParkExtraEnhancer;
import pbouda.jeffrey.provider.writer.sql.enhancer.TlabAllocationSamplesExtraEnhancer;
import pbouda.jeffrey.provider.writer.sql.enhancer.WallClockSamplesExtraEnhancer;
import pbouda.jeffrey.provider.writer.sql.enhancer.WallClockSamplesWeightEnhancer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class WriterResultCollector {

    private final DatabaseWriter<EnhancedEventType> eventTypeWriter;
    private final DatabaseWriter<EventThreadWithId> threadWriter;

    private EventWriterResult combined = new EventWriterResult(
            new ArrayList<>(), new ArrayList<>(), new HashMap<>(), new HashSet<>(), Instant.MIN);

    public WriterResultCollector(
            DatabaseWriter<EnhancedEventType> eventTypeWriter,
            DatabaseWriter<EventThreadWithId> threadWriter) {

        this.eventTypeWriter = eventTypeWriter;
        this.threadWriter = threadWriter;
    }

    public void add(EventWriterResult newResults) {
        List<EventTypeBuilder> events =
                combineEventTypes(combined.eventTypes(), newResults.eventTypes());

        Map<String, ActiveSetting> activeSettings =
                combineActiveSettings(combined.activeSettings(), newResults.activeSettings());

        List<EventThreadWithId> threads = new ArrayList<>();
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
        List<EventThreadWithId> modifiedThreads = new EventThreadCleaner()
                .clean(combined.eventThreads());

        modifiedThreads.forEach(threadWriter::insert);
    }

    private List<EventTypeEnhancer> resolveEventTypeEnhancers(ActiveSettings settings) {
        return List.of(
                new ExecutionSamplesExtraEnhancer(settings),
                new WallClockSamplesExtraEnhancer(),
                new NativeMallocAllocationSamplesExtraEnhancer(),
                new TlabAllocationSamplesExtraEnhancer(settings),
                new MonitorEnterExtraEnhancer(settings),
                new MonitorWaitExtraEnhancer(),
                new ThreadParkExtraEnhancer(settings),
                new WallClockSamplesWeightEnhancer(settings),
                new ExecutionSamplesWeightEnhancer(settings)
        );
    }

    private static void applyEnhancers(List<EventTypeEnhancer> enhancers, EventTypeBuilder builder) {
        Type type = Type.fromCode(builder.getEventType().name());
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

            if (builderOpt.isPresent()) {
                builderOpt.get().mergeWith(builder);
            } else {
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
